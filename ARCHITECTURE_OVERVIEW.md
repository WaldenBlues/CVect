# CVect 架构总览（2026-03-01）

本文档基于当前仓库实际代码更新（不是目标蓝图），覆盖后端、前端、模型服务与运维脚本。

## 1. 项目定位

CVect 是一个 HR 简历处理与检索系统，核心能力包括：

- JD 管理（创建/编辑/删除）。
- 简历上传（多文件与 ZIP）、解析、结构化抽取与候选人入库。
- 双队列异步处理（上传处理队列 + 向量入库队列）。
- 候选人实时流式更新（SSE）。
- 基于向量检索的语义匹配排序。

## 2. 仓库结构与边界

```text
CVect/
├── backend/cvect/        # Spring Boot 后端
├── frontend/             # Vue 3 + Vite 前端
├── Qwen/                 # Python Embedding/Generation 服务 + 数据生成脚本
├── infra/vllm/           # vLLM + nginx 网关编排
├── scripts/              # vLLM 启停与冒烟测试脚本
├── docker-compose.yml    # PostgreSQL (pgvector 镜像)
├── ARCHITECTURE_OVERVIEW.md
└── CODE_DESIGN_REPORT.md
```

边界原则：

- `backend/cvect` 负责业务编排、数据一致性、队列消费与 API。
- `frontend` 负责运营台交互、SSE 消费、语义排序展示。
- `Qwen` 与 `infra/vllm` 提供模型侧能力（embedding / chat / 数据生成）。
- 数据持久化主库为 PostgreSQL；本地测试可退回 H2。

## 3. 运行拓扑

### 3.1 在线业务路径

1. 浏览器访问 `frontend`（Vite 开发期代理 `/api` 到 `backend`）。
2. 前端调用后端 REST API 与 SSE：
   - 候选人流：`/api/candidates/stream`
   - 批次流：`/api/sse/batches/{batchId}`（兼容：`/api/uploads/batches/{batchId}/stream`）
3. 后端将业务数据写入 PostgreSQL（包含上传队列与向量入库任务）。
4. 后端调用 Python embedding 服务（默认 `http://localhost:8001/embed`）生成向量。

### 3.2 模型服务路径（可选）

- `scripts/vllm-up.sh` 启动 `infra/vllm/docker-compose.yml`：
  - LLM: `:8000`
  - Embedding: `:8001`
  - 统一网关: `:8002`
- `nginx.conf` 将 `/v1/embeddings` 转发到 embedding 服务，其余 `/v1/*` 转发到 LLM。

## 4. 后端核心架构

后端包结构（`src/main/java/com/walden/cvect`）当前共 `97` 个类：

- `web`（14）：Controller + SSE/Stream 事件模型。
- `service`（12）：业务编排、队列消费、快照服务。
- `repository`（13）：JPA + Native SQL。
- `model`（40）：实体、抽取规则、事实模型。
- `infra`（9）：解析、embedding、向量存储。
- `config`（4）：线程池、索引、维度一致性校验。
- `actuator`（2）：上传与向量队列性能观测端点。
- `exception`（2）与应用入口（1）。

### 4.1 两条异步队列

#### A. 上传处理队列（`upload_items`）

状态枚举（实际代码）：

- `QUEUED`, `PROCESSING`, `DONE`, `DUPLICATE`, `FAILED`

实际主流程使用：`QUEUED -> PROCESSING -> DONE/DUPLICATE/FAILED`。

关键机制：

- `UploadItemJpaRepository.claimNextQueuedBatch()` 使用 `FOR UPDATE SKIP LOCKED` 批量 claim。
- `queue_job_key` 作为租约令牌，防止过期 worker 回写覆盖。
- stale lease 恢复：`recoverStaleProcessingLease(...)`。
- 批次进度由 `UploadBatchJpaRepository.refreshProgressFromItems(...)` 单 SQL 原子聚合。

#### B. 向量入库队列（`vector_ingest_tasks`）

状态枚举：`PENDING`, `PROCESSING`, `DONE`, `FAILED`。

关键机制：

- `claimNextPendingBatch()` 同样采用 `SKIP LOCKED`。
- 失败按 `attempt` + `max-attempts` 重试。
- embedding 连接类故障（connection refused/timeout）按可恢复故障处理，回到 `PENDING`。
- stale task 恢复：`recoverStaleProcessing(...)`。

## 5. 关键数据模型

核心表（实体层）：

- `job_descriptions`（JD 主体）。
- `candidates`（候选人主记录，`(file_hash, jd_id)` 唯一）。
- `candidate_snapshots`（用于 SSE/列表快速读取的快照聚合）。
- `upload_batches`（上传批次）。
- `upload_items`（批次内文件任务，含 queue lease 字段）。
- `vector_ingest_tasks`（向量入库任务）。
- `resume_chunks`（向量 chunk 存储）。
- `contacts` / `educations` / `honors` / `links`（结构化事实表）。

迁移策略现状：

- Flyway 迁移已补齐到 `V3`（基线建表 + 历史上传状态收敛）。
- 运行时 `spring.jpa.hibernate.ddl-auto=validate`，由 Flyway 统一管理 schema。

## 6. 主业务链路

### 6.1 上传与解析链路

1. `UploadController` 接收 `/api/uploads/resumes` 或 `/api/uploads/zip`。
2. 文件保存到 `app.upload.storage-dir`（默认 `storage` 绝对路径）。
3. 入库 `upload_items`，状态置为 `QUEUED` 并生成 `queue_job_key`。
4. `UploadQueueWorkerRunner` 启动多消费者，调用 `UploadQueueWorkerService.consumeQueuedItems()`。
5. `ResumeProcessService.process(...)` 执行：
   - Tika 解析
   - 文本标准化 + 分块
   - 事实抽取写库
   - 候选人去重与入库
   - EXPERIENCE/SKILL 分块投递到向量队列
6. 处理完成后刷新批次进度，并推送批次 SSE。

### 6.2 向量入库链路

1. `VectorIngestService.ingest(...)` 将任务写入 `vector_ingest_tasks`。
2. `VectorIngestQueueWorkerRunner` 拉起消费者。
3. `VectorIngestQueueWorkerService` 处理任务，调用 `VectorStoreService.save(...)`。
4. 全量完成时推送 `VectorStatusStreamEvent(status=VECTOR_DONE)` 到候选人 SSE。

### 6.3 语义检索链路

1. `SearchController` 接收 `/api/search`。
2. 用 `EmbeddingService` 对 JD 文本生成查询向量。
3. `VectorStoreService.search(...)` 在 `resume_chunks` 执行相似度检索。
4. 按 candidate 聚合 chunk 分数并按 Experience/Skill 权重排序。
5. 可选 `onlyVectorReadyCandidates` 过滤未完成向量化候选人。

## 7. 前端架构（Vue 单页）

前端当前为单页聚合实现（`App.vue`）：

- JD 管理：创建/编辑/删除/选择。
- 简历上传：拖拽 + 文件选择，支持 ZIP。
- 候选人列表：分页、筛选、招聘状态更新。
- SSE：订阅 `candidate` 与 `vector` 事件并增量更新 UI。
- 语义排序：调用 `/api/search`，以后端返回分数与顺序为准，前端仅做展示与筛选。

前端工具模块：

- `src/utils/semanticMatching.js`：检索 payload 构建、权重建议、排名映射。
- `src/utils/semanticMatching.test.mjs`：Node 原生测试。

## 8. 模型与数据生成子系统（Qwen）

`Qwen/embedding_service.py`：FastAPI 统一服务，提供：

- `POST /embed`
- `POST /generate`
- `GET /health`
- `GET /info`

`Qwen/dataGen.py` + `run_datagen_long.py`：批量生成多格式简历数据（PDF/DOCX/TXT/MD），支持长任务恢复与重试。

## 9. 配置与环境要点

后端关键配置（`application.yml`）：

- `spring.datasource.*`：默认连接本机 PostgreSQL。
- `app.upload.worker.*`：上传队列线程数、claim 批量、stale 超时等。
- `app.vector.*`：向量开关、维度、并发写限流。
- `app.vector.ingest.worker.*`：向量队列消费者配置。
- `app.embedding.*`：embedding 服务 URL、维度、超时。

启动期守卫：

- `VectorDimensionConsistencyValidator` 强制 embedding 维度与 vector 维度一致。

## 10. 可观测性与运维

Actuator 端点：

- `/actuator/uploadperformance`：上传队列等待/处理/总时长分位。
- `/actuator/vectoringestperformance`：向量队列积压与处理时延。

业务健康：

- `/api/resumes/health`
- `/api/vector/health`（embedding 可达性 + 向量任务统计）

## 11. 当前架构特征与约束

优势：

- 不依赖外部 MQ，部署简单。
- 双队列与租约机制保证并发安全与恢复能力。
- SSE + 快照组合满足实时 UI 运营需求。

约束：

- 前端逻辑高度集中在 `App.vue`，可维护性受限。
