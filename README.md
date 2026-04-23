# CVect

![Java 17](https://img.shields.io/badge/Java-17-2f5d95?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f?style=flat-square)
![Vue 3](https://img.shields.io/badge/Vue-3-42b883?style=flat-square)
![FastAPI](https://img.shields.io/badge/FastAPI-Embedding%20Service-009688?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-336791?style=flat-square)
![Docker Compose](https://img.shields.io/badge/Docker%20Compose-Web%20%2B%20Embedding-2496ed?style=flat-square)
![Qwen CPU](https://img.shields.io/badge/Qwen-CPU%20Embedding-8b5cf6?style=flat-square)

CVect 是一个面向招聘场景的简历处理与候选人管理系统，覆盖 `JD 管理`、`简历上传解析`、`候选人列表`、`SSE 实时流` 和 `pgvector` 语义检索。

项目当前默认形态已经收敛为：

- 三个编排入口：`docker-compose.yml`、`docker-compose.web.yml`、`docker-compose.embedding.yml`
- 三个配置文件：`.env`、`.env.web`、`.env.embedding`
- 一个可单独部署的模型服务：`Qwen`
- 三个运行脚本：`scripts/local-run.sh`、`scripts/server-run.sh`、`scripts/embedding-run.sh`

## Preview

<p align="center">
  <img src="./image.png" alt="CVect Demo Preview" />
</p>

## Live Demo

| Item | Value |
| --- | --- |
| Public URL | [http://111.228.5.197:8088/](http://111.228.5.197:8088/) |
| Username | `demo` |
| Password | `demo123` |
| Access | Interviewer / demo access |

## Feature Highlights

- JD create / update / delete
- Resume upload, ZIP upload, retry, dedupe
- Tika parsing and structured extraction
- Candidate list and recruitment status updates
- SSE batch and candidate streaming
- Vector search backed by `pgvector`
- Offline Hugging Face cache for `Qwen/Qwen3-Embedding-0.6B`

## Stack

- Backend: Java 17, Spring Boot 3.5, Flyway, JPA
- Frontend: Vue 3, Vite 5
- Model service: FastAPI, Transformers, PyTorch CPU
- Database: PostgreSQL 17, `pgvector`
- Infra: Docker Compose

## Architecture

```text
web host:
frontend -> nginx -> backend -> postgres
                       |
                       -> remote embedding service

embedding host:
qwen -> :8001
```

## Project Layout

```text
CVect/
├── backend/cvect/        # Spring Boot backend
├── frontend/             # Vue app
├── Qwen/                 # FastAPI embedding service
├── scripts/              # run / deploy / cache scripts
├── docker-compose.yml    # local all-in-one compose
├── docker-compose.web.yml
├── docker-compose.embedding.yml
├── .env                  # local development env
├── .env.web              # web host env
└── .env.embedding        # embedding host env
```

## Requirements

- Java 17
- Node.js 20+
- Python 3.10+
- Docker + Docker Compose

## Quick Start

### Local Development

Edit [`.env`](./.env) first, then run:

If `CVECT_BASIC_AUTH_ENABLED=true`, set `CVECT_BASIC_AUTH_USERNAME` and `CVECT_BASIC_AUTH_PASSWORD` in `.env` before starting; the checked-in defaults intentionally leave those blank.

```bash
scripts/local-run.sh start
```

Useful commands:

```bash
scripts/local-run.sh status
scripts/local-run.sh stop
scripts/local-run.sh restart
```

Local mode behavior:

- `postgres` runs via Docker Compose
- `qwen` runs locally on `:8001`
- `backend` runs locally on `:8080`
- `frontend` runs locally on `:5173`
- logs and pid files are written under `.run/`

### Web Deployment

`scripts/server-run.sh` now manages only the web stack:

- `postgres`
- `backend`
- `frontend`

Edit [`.env.web`](./.env.web) before starting the web host. It must point at the remote embedding service:

```bash
CVECT_EMBEDDING_SERVICE_URL=http://<embedding-host>:8001/embed
CVECT_EMBEDDING_HEALTH_URL=http://<embedding-host>:8001/ready
```

Then start the web host:

```bash
scripts/server-run.sh up
```

Useful commands:

```bash
scripts/server-run.sh status
scripts/server-run.sh logs
scripts/server-run.sh logs backend
scripts/server-run.sh down
scripts/server-run.sh restart
```

`scripts/server-run.sh up` starts from existing images and will not build on the server. If you intentionally want to build the web images on the server, use `scripts/server-run.sh up-build`.

If [`.env.web`](./.env.web) still points to `http://qwen:8001/...`, `scripts/server-run.sh` fails fast instead of starting a local embedding container on the web host.

### Embedding Deployment

`scripts/embedding-run.sh` manages only the `qwen` service and is intended for the future GPU host.

Edit [`.env.embedding`](./.env.embedding) before starting the embedding host.

```bash
scripts/embedding-run.sh up-build
```

Useful commands:

```bash
scripts/embedding-run.sh status
scripts/embedding-run.sh logs
scripts/embedding-run.sh restart
scripts/embedding-run.sh down
```

The embedding host publishes `qwen` on `CVECT_EMBEDDING_PUBLIC_PORT`. Point the web host at that address through `CVECT_EMBEDDING_SERVICE_URL` and `CVECT_EMBEDDING_HEALTH_URL`.

### Offline HF Cache

The default model is `Qwen/Qwen3-Embedding-0.6B`.
The cache is prepared locally and mounted into the `qwen` container at `/root/.cache/huggingface`.

Prepare cache locally:

```bash
scripts/qwen-offline-cache.sh prefetch
scripts/qwen-offline-cache.sh verify
scripts/qwen-offline-cache.sh pack
```

Available cache commands:

```bash
scripts/qwen-offline-cache.sh info
scripts/qwen-offline-cache.sh prefetch
scripts/qwen-offline-cache.sh verify
scripts/qwen-offline-cache.sh pack
scripts/qwen-offline-cache.sh unpack /path/to/qwen-hf-cache.tgz
```

### Local Build, Remote Load

If you do not want remote machines to build images:

1. Build locally
2. Export the web images and embedding image separately
3. Upload the matching image archive to each host
4. Upload HF cache only to the embedding host
5. Start each role with its dedicated script

Local:

```bash
docker compose --env-file .env -f docker-compose.yml build qwen backend frontend
docker pull m.daocloud.io/docker.io/pgvector/pgvector:pg17
docker save -o /tmp/cvect-web-images.tar \
  cvect-backend:latest \
  cvect-frontend:latest \
  m.daocloud.io/docker.io/pgvector/pgvector:pg17
docker save -o /tmp/cvect-embedding-images.tar \
  cvect-qwen:latest
gzip -c /tmp/cvect-web-images.tar > .artifacts/cvect-web-images.tgz
gzip -c /tmp/cvect-embedding-images.tar > .artifacts/cvect-embedding-images.tgz
scripts/qwen-offline-cache.sh pack
```

Web host:

```bash
cp .env.web /opt/cvect/.env.web
gunzip -c cvect-web-images.tgz | docker load
scripts/server-run.sh up
scripts/server-run.sh status
```

Embedding host:

```bash
cp .env.embedding /opt/cvect/.env.embedding
gunzip -c cvect-embedding-images.tgz | docker load
scripts/qwen-offline-cache.sh unpack /path/to/qwen-hf-cache.tgz
scripts/qwen-offline-cache.sh verify
scripts/embedding-run.sh up
scripts/embedding-run.sh status
```

## Validation

### Health Checks

Local mode:

```bash
curl -fsS http://127.0.0.1:8001/health
curl -fsS http://127.0.0.1:8080/api/resumes/health
```

Web host:

```bash
curl -u '<user>:<pass>' -fsS http://127.0.0.1:8088/healthz
scripts/server-run.sh status
```

Embedding host:

```bash
curl -fsS http://127.0.0.1:8001/ready
scripts/embedding-run.sh status
```

### Tests

Backend:

```bash
cd backend/cvect
./mvnw -q test
```

Frontend:

```bash
cd frontend
npm test
```

Python syntax/import checks:

```bash
python3 -m py_compile Qwen/embedding_service.py
```

## Key Configuration

Runtime behavior is split across the role-specific env files:

- local development: [`.env`](./.env)
- web host: [`.env.web`](./.env.web)
- embedding host: [`.env.embedding`](./.env.embedding)

Important keys:

- `CVECT_HTTP_PORT`
- `CVECT_DB_URL`
- `CVECT_DB_USERNAME`
- `CVECT_DB_PASSWORD`
- `CVECT_EMBEDDING_SERVICE_URL`
- `CVECT_EMBEDDING_API_FORMAT`
- `CVECT_EMBEDDING_HEALTH_URL`
- `CVECT_EMBEDDING_PUBLIC_PORT`
- `CVECT_EMBEDDING_MODEL`
- `CVECT_EMBEDDING_BATCH_SIZE`
- `CVECT_EMBEDDING_MAX_INPUT_LENGTH`
- `CVECT_CHUNK_MAX_LENGTH`
- `CVECT_VECTOR_ENABLED`
- `CVECT_VECTOR_INGEST_WORKER_ENABLED`
- `CVECT_HF_CACHE_DIR`
- `CVECT_HF_HUB_OFFLINE`
- `CVECT_HF_LOCAL_FILES_ONLY`
- `JAVA_OPTS`

Small machine defaults are already tuned for `2C4G`:

- CPU-only embedding
- batch size `1`
- max input length `1024`
- conservative JVM memory

## Deployment Notes

- Live demo URL can be linked from the GitHub repository "About" section.
- Web and embedding are now separate deployment roles.
- `qwen` should live on the embedding host only.
- Frontend auth is configured at the nginx layer and should be managed via `.env.web`.
- `scripts/local-run.sh` remains the all-in-one local development entrypoint.
