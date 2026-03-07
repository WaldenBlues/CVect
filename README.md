# CVect

CVect 是一个面向招聘场景的简历处理与候选人管理系统。当前仓库包含 4 个主要部分：

- `backend/cvect`：Spring Boot 后端，负责 JD、上传批次、解析抽取、候选人查询、SSE 推送和向量检索
- `frontend`：Vue 3 单页控制台，负责 JD 管理、实时候选人列表、语义重排和状态流转
- `Qwen`：本地 FastAPI 模型服务，提供 embedding 和文本生成接口
- `infra/vllm`：可选的 OpenAI 兼容 vLLM 网关编排

项目当前没有鉴权、多租户或生产部署编排；README 以本地开发和联调为主。

## 当前实现

- JD 管理：新增、列表、详情、编辑、删除
- 简历上传：多文件上传、ZIP 上传、失败项重试、幂等去重
- 简历处理：Apache Tika 解析、事实抽取、分块、异步向量入库
- 候选人视图：按 JD 查询、实时增量流、招聘状态更新
- 语义检索：按 JD 文本进行向量召回，支持 Experience / Skill 加权
- 运行支撑：Flyway 迁移、Actuator、pgvector、H2 测试配置

前端现在是一个面向招聘运营的单页看板，不是通用 UI 框架。

## 技术栈

- Backend：Java 17、Spring Boot 3.5、Spring Web / WebFlux、Spring Data JPA、Flyway
- Parser：Apache Tika 3
- Database：PostgreSQL 17 + `pgvector`
- Frontend：Vue 3、Vite 5
- Model service：FastAPI、Transformers、PyTorch
- Realtime：SSE
- Tests：JUnit 5、Mockito、AssertJ、Testcontainers

## 仓库结构

```text
CVect/
├── backend/cvect/              # Spring Boot 后端
├── frontend/                   # Vue 单页前端
├── Qwen/                       # FastAPI embedding / generation 服务与数据生成脚本
├── infra/vllm/                 # 可选 vLLM + nginx 网关
├── scripts/                    # vLLM 启停与 smoke test
├── docker-compose.yml          # 本地 PostgreSQL (pgvector)
├── run.sh                      # 本地一键启动脚本
├── ARCHITECTURE_OVERVIEW.md    # 架构说明
└── CODE_DESIGN_REPORT.md       # 设计分析
```

## 系统流转

```text
JD -> 上传简历 -> Tika 解析 -> 事实抽取 / 分块 -> 向量任务入队 -> pgvector 入库
                                                      |
                                                      +-> SSE 推送批次进度与候选人增量

前端 -> REST 查询 JD / 候选人 / 批次
前端 -> POST /api/search -> embedding service -> pgvector 检索 -> 候选人语义重排
```

## 环境要求

推荐在 Linux/macOS 本地开发环境运行，并预先安装：

- Java 17
- Node.js 18+
- Python 3.10+
- Docker + Docker Compose

首次启动前建议确认：

- 机器可以拉取 Hugging Face 模型，或者本地已有缓存
- `5432`、`5173`、`8001`、`8080` 端口未被占用
- 如果要启用 `infra/vllm`，需要可用 GPU，并避免与本地 embedding 服务同时占用 `8001`

## 首次准备

### 1. Python 模型服务依赖

```bash
cd Qwen
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. 前端依赖

```bash
cd frontend
npm install
```

后端使用 Maven Wrapper，不需要单独安装 Maven。

## 快速启动

### 推荐方式：一键启动

在仓库根目录执行：

```bash
./run.sh start
```

该脚本会尝试启动：

- PostgreSQL：`localhost:5432`
- Qwen FastAPI embedding 服务：`localhost:8001`
- Spring Boot 后端：`localhost:8080`
- Vite 前端：`localhost:5173`

常用命令：

```bash
./run.sh status
./run.sh stop
./run.sh restart
```

说明：

- `run.sh` 会在 `frontend/node_modules` 缺失时自动执行 `npm install`
- `run.sh` 不会自动安装 Python 依赖，`Qwen` 环境需要先准备好
- 脚本日志输出到 `.run/logs/`

### 手动分服务启动

#### PostgreSQL

```bash
docker compose up -d postgres
```

#### Qwen 模型服务

```bash
cd Qwen
source .venv/bin/activate
python3 embedding_service.py
```

默认接口：

- `GET /health`
- `GET /info`
- `POST /embed`
- `POST /generate`
- `POST /models/preload`

#### 后端

```bash
cd backend/cvect
./mvnw -Dmaven.test.skip=true spring-boot:run
```

#### 前端

```bash
cd frontend
npm run dev -- --host
```

## 健康检查

```bash
curl -s http://localhost:8080/api/resumes/health
curl -s http://localhost:8080/api/vector/health
curl -s http://localhost:8001/health
```

## 核心接口

### JD

- `GET /api/jds`
- `POST /api/jds`
- `GET /api/jds/{id}`
- `PUT /api/jds/{id}`
- `DELETE /api/jds/{id}`

### 上传与批次

- `POST /api/uploads/resumes`
- `POST /api/uploads/zip`
- `GET /api/uploads/batches/{id}`
- `GET /api/uploads/batches/{id}/items`
- `POST /api/uploads/batches/{id}/retry-failed`

### SSE

- `GET /api/candidates/stream`
- `GET /api/sse/batches/{id}`
- `GET /api/uploads/batches/{id}/stream`：兼容旧路径

### 候选人

- `GET /api/candidates?jdId=...`
- `PATCH /api/candidates/{id}/recruitment-status`

### 解析与向量

- `POST /api/resumes/parse`
- `GET /api/resumes/health`
- `POST /api/search`
- `POST /api/search/admin/create-index`
- `GET /api/vector/health`

`POST /api/search` 支持：

- `jobDescription`
- `topK`
- `filterByExperience`
- `filterBySkill`
- `experienceWeight`
- `skillWeight`
- `onlyVectorReadyCandidates`

## 关键配置

主配置文件是 `backend/cvect/src/main/resources/application.yml`。

### 数据库

- `CVECT_DB_URL`，默认 `jdbc:postgresql://localhost:5432/cvect`
- `CVECT_DB_USERNAME`，默认 `postgres`
- `CVECT_DB_PASSWORD`，默认 `postgres`

### 服务端口

- `CVECT_SERVER_PORT`，默认 `8080`

### Embedding

- `CVECT_EMBEDDING_SERVICE_URL`，默认 `http://localhost:8001/embed`
- `CVECT_EMBEDDING_MODEL`
- `CVECT_EMBEDDING_TIMEOUT_SECONDS`
- `CVECT_EMBEDDING_BATCH_SIZE`
- `CVECT_EMBEDDING_DIMENSION`，默认 `1024`
- `CVECT_EMBEDDING_MAX_INPUT_LENGTH`

### Vector

- `CVECT_VECTOR_ENABLED`，默认 `true`
- `CVECT_VECTOR_TABLE`，默认 `resume_chunks`
- `CVECT_VECTOR_INDEX_TYPE`，默认 `hnsw`
- `CVECT_VECTOR_METRIC`，默认 `cosine`
- `CVECT_VECTOR_DIMENSION`，默认继承 embedding 维度
- `CVECT_VECTOR_MAX_CONCURRENT_WRITES`

### Upload

- `CVECT_UPLOAD_STORAGE_DIR`，默认 `storage`
- `CVECT_UPLOAD_MAX_INFLIGHT_ITEMS`，默认 `2000`
- `CVECT_UPLOAD_MAX_FILES_PER_ZIP`，默认 `2000`

需要注意：

- `CVECT_EMBEDDING_DIMENSION` 与 `CVECT_VECTOR_DIMENSION` 必须一致
- 切换 embedding 模型前，需要确认向量表维度和现有索引是否仍然兼容
- 默认运行时 schema 由 Flyway 管理，`ddl-auto=validate`

## 数据库与迁移

后端迁移文件位于 `backend/cvect/src/main/resources/db/migration`，当前包含：

- `V1__baseline.sql`
- `V2__candidate_file_hash_jd_unique.sql`
- `V3__schema_and_upload_status_convergence.sql`

开发备用配置位于 `backend/cvect/src/main/resources/application-h2.yml`，用于 H2 内存库场景。

## 测试

### 后端

```bash
cd backend/cvect
./mvnw -q test
```

当前测试覆盖：

- Controller 层接口
- Tika 解析与事实抽取
- 上传队列与向量入库 worker
- 向量检索和维度配置校验
- 完整管线集成测试

默认 Surefire 配置排除了 `postgres` 分组测试；需要这类测试时，建议单独按类或分组运行。

### 前端

```bash
cd frontend
npm test
```

当前前端测试主要覆盖语义匹配工具函数：`src/utils/semanticMatching.test.mjs`。

## 可选：vLLM OpenAI 兼容网关

如果你需要本地 OpenAI 兼容接口，可以使用 `infra/vllm`：

```bash
./scripts/vllm-up.sh
./scripts/vllm-smoke-test.sh
./scripts/vllm-down.sh
```

默认端口：

- LLM：`http://localhost:8000`
- Embedding：`http://localhost:8001`
- Gateway：`http://localhost:8002`

注意：

- 这套编排依赖 GPU
- `vllm_emb` 也会占用 `8001`，不要和根目录 `run.sh` 启动的 `Qwen/embedding_service.py` 同时运行

## 已知边界

- 当前 README 描述的是本地开发路径，不等同于生产部署方案
- 前端是单页控制台，没有登录、权限或国际化
- `Qwen` 目录里除了在线服务，还包含数据生成脚本和生成产物，不属于运行时必需内容

如果你要继续整理架构和设计细节，优先看：

- `ARCHITECTURE_OVERVIEW.md`
- `CODE_DESIGN_REPORT.md`
