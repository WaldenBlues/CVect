# CVect

CVect 是一个面向 HR 场景的简历处理与候选人管理系统，覆盖 JD 管理、批量上传、解析抽取、实时进度推送（SSE）以及语义检索重排。

## 当前能力（已实现）

- JD 管理：新增 / 列表 / 详情 / 编辑 / 删除
- 简历处理链路：上传 -> 解析（Apache Tika）-> 分块 -> 事实抽取 -> 入库
- 批量上传：多文件上传、ZIP 上传、失败项重试（幂等）
- 候选人管理：列表查询、招聘状态流转（`TO_CONTACT` / `TO_INTERVIEW` / `REJECTED`）
- 实时能力：
  - 候选人增量流：`/api/candidates/stream`
  - 批次进度流：`/api/sse/batches/{id}`（兼容旧路径）
- 向量能力：
  - 异步向量入库任务队列（PENDING/PROCESSING/DONE/FAILED）
  - 语义检索接口 `POST /api/search`
  - HNSW 索引创建接口 `POST /api/search/admin/create-index`
  - 向量健康检查 `GET /api/vector/health`
- 前端语义匹配面板：支持 Experience / Skill 权重滑杆与自动调参

## 技术栈

- Backend：Java 17, Spring Boot 3.5, Spring Data JPA, Hibernate, Flyway
- Parser：Apache Tika
- Database：PostgreSQL（运行）+ pgvector, H2（测试）
- Realtime：SSE
- Frontend：Vue 3 + Vite
- Embedding 调用：Spring WebClient -> 本地 Python 服务（`Qwen/embedding_service.py`）

## 目录结构

```text
CVect/
├── backend/cvect/        # Spring Boot 后端
├── frontend/             # Vue 前端
├── Qwen/                 # 本地模型服务（FastAPI）
├── infra/vllm/           # vLLM + nginx 网关编排
├── scripts/              # vLLM 启停与 smoke test
├── docker-compose.yml    # 本地 PostgreSQL（pgvector）
└── run.sh                # 一键启动/停止本地开发服务
```

## 快速启动（推荐）

### 1) 一键启动

在项目根目录执行：

```bash
./run.sh start
```

该脚本会启动：

- PostgreSQL（Docker, `:5432`）
- Embedding 服务（FastAPI, `:8001`）
- Spring Boot 后端（`:8080`）
- Vite 前端（`:5173`）

常用命令：

```bash
./run.sh status
./run.sh stop
./run.sh restart
```

### 2) 健康检查

```bash
curl -s http://localhost:8080/api/resumes/health
curl -s http://localhost:8080/api/vector/health
curl -s http://localhost:8001/health
```

## 手动启动（分服务）

### PostgreSQL

```bash
docker compose up -d postgres
```

### Embedding 服务

```bash
cd Qwen
python3 embedding_service.py
```

### 后端

```bash
cd backend/cvect
./mvnw -Dmaven.test.skip=true spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev -- --host
```

## 关键配置（默认值）

配置文件：`backend/cvect/src/main/resources/application.yml`

- 数据库：`CVECT_DB_URL` / `CVECT_DB_USERNAME` / `CVECT_DB_PASSWORD`
- Embedding：
  - `CVECT_EMBEDDING_SERVICE_URL`（默认 `http://localhost:8001/embed`）
  - `CVECT_EMBEDDING_DIMENSION`（默认 `1024`）
- 向量：
  - `CVECT_VECTOR_ENABLED`（默认 `true`）
  - `CVECT_VECTOR_DIMENSION`（默认继承 embedding 维度）
- 上传限流：
  - `CVECT_UPLOAD_MAX_INFLIGHT_ITEMS`（默认 `2000`）
  - `CVECT_UPLOAD_MAX_FILES_PER_ZIP`（默认 `2000`）

> 注意：向量维度必须一致。若你切换 embedding 模型，请同步确认库表维度与 `CVECT_EMBEDDING_DIMENSION`/`CVECT_VECTOR_DIMENSION`。

## 主要接口

- JD
  - `GET /api/jds`
  - `POST /api/jds`
  - `GET /api/jds/{id}`
  - `PUT /api/jds/{id}`
  - `DELETE /api/jds/{id}`
- 上传与批次
  - `POST /api/uploads/resumes`
  - `POST /api/uploads/zip`
  - `GET /api/uploads/batches/{id}`
  - `GET /api/uploads/batches/{id}/items`
  - `POST /api/uploads/batches/{id}/retry-failed`
- 候选人
  - `GET /api/candidates?jdId=...`
  - `PATCH /api/candidates/{id}/recruitment-status`
  - `GET /api/candidates/stream`
- 解析
  - `POST /api/resumes/parse`
  - `GET /api/resumes/health`
- 语义检索
  - `POST /api/search`
  - `POST /api/search/admin/create-index`
  - `GET /api/vector/health`
- 批次 SSE
  - `GET /api/sse/batches/{id}`
  - `GET /api/uploads/batches/{id}/stream`（兼容）

## vLLM 本地网关（可选）

如果你需要 OpenAI 兼容网关（`/v1/chat/completions` + `/v1/embeddings`），可使用 `infra/vllm`：

```bash
./scripts/vllm-up.sh
./scripts/vllm-smoke-test.sh
```

默认端口：

- LLM：`http://localhost:8000`
- Embedding：`http://localhost:8001`
- 网关：`http://localhost:8002`

## 测试

```bash
cd backend/cvect
./mvnw -q -DskipTests compile
./mvnw -q test
```

前端工具函数测试：

```bash
cd frontend
npm test
```
