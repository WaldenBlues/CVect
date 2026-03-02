# CODE DESIGN REPORT（2026-03-01）

本文档从代码层面描述当前实现，而非目标方案。

范围：

- 后端：`backend/cvect/src/main/java`（当前 `97` 个 Java 类）
- 前端：`frontend/src`
- Python/模型侧：`Qwen/` 与 `infra/vllm/`

## 1. 代码资产概览

### 1.1 后端主代码统计

- 总类数：`97`
- 包分布：
  - `web`: 14
  - `service`: 12
  - `repository`: 13
  - `model`: 40
  - `infra`: 9
  - `config`: 4
  - `actuator`: 2
  - `exception`: 2
  - 根入口：1

### 1.2 测试代码统计

- 后端测试：`27` 个测试类（单元 + 集成）
- 前端测试：`semanticMatching.test.mjs`（Node 原生 test）

## 2. 分层与依赖方向

后端遵循“Controller -> Service -> Repository/Infra -> DB/外部服务”主方向。

- `web.controller` 调用 `service` 和部分 `repository`。
- `service` 负责业务编排与事务边界。
- `repository` 封装 JPA + native SQL。
- `infra` 与外部系统交互（Tika、Embedding HTTP、pgvector SQL）。
- `actuator` 通过 `JdbcTemplate` 提供观测查询。

注意：当前代码中 `controller` 与 `repository` 存在少量直连（例如 `UploadController` 直接入队），属于“薄 service”风格而非严格 DDD。

## 3. 后端包级设计

## 3.1 `web`（接口与流式通道）

Controller 列表：

- `UploadController`
- `UploadBatchController`
- `BatchSseController`
- `ResumeController`
- `JobDescriptionController`
- `CandidateController`
- `CandidateStreamController`
- `SearchController`
- `VectorHealthController`

流式/SSE组件：

- `BatchStreamService` + `BatchStreamEvent`
- `CandidateStreamService` + `CandidateStreamEvent`
- `VectorStatusStreamEvent`

### 3.1.1 API 路由清单（当前代码）

- 上传：
  - `POST /api/uploads/resumes`
  - `POST /api/uploads/zip`
- 批次：
  - `GET /api/uploads/batches/{id}`
  - `GET /api/uploads/batches/{id}/items`
  - `POST /api/uploads/batches/{id}/retry-failed`
- 批次 SSE：
  - `GET /api/sse/batches/{batchId}`（兼容：`GET /api/uploads/batches/{id}/stream`）
- 简历：
  - `POST /api/resumes/parse`
  - `GET /api/resumes/health`
- JD：
  - `GET/POST /api/jds`
  - `GET/PUT/DELETE /api/jds/{id}`
- 候选人：
  - `GET /api/candidates?jdId=...`
  - `PATCH /api/candidates/{id}/recruitment-status`
  - `GET /api/candidates/stream`
- 搜索：
  - `POST /api/search`
  - `POST /api/search/admin/create-index`
- 向量健康：
  - `GET /api/vector/health`

## 3.2 `service`（业务编排与异步消费）

关键类：

- `ResumeProcessService`
- `UploadQueueWorkerService`, `UploadQueueWorkerRunner`
- `VectorIngestService`
- `VectorIngestQueueWorkerService`, `VectorIngestQueueWorkerRunner`
- `UploadBatchService`
- `ResumeFactService`
- `CandidateSnapshotService`
- `DefaultChunkerService`

职责特征：

- `Runner` 负责常驻轮询线程生命周期（`@PostConstruct` 拉起，`@PreDestroy` 停止）。
- `WorkerService` 负责 claim/process/recovery 的事务流程。
- `ResumeProcessService` 是上传处理主编排入口。

## 3.3 `repository`（数据访问）

关键点：

- `UploadItemJpaRepository`：
  - `claimNextQueuedBatch()` 使用 `FOR UPDATE SKIP LOCKED`
  - success/failure 回写必须匹配 `queue_job_key`
  - stale lease 恢复与 job key 修复
- `UploadBatchJpaRepository`：
  - `refreshProgressFromItems()` 原子聚合批次进度
- `VectorIngestTaskJpaRepository`：
  - 向量任务 claim/recover/complete 查询
- `FactRepository`：事实写入 Facade，降低 service 对多表耦合

## 3.4 `model`（实体与抽取规则）

实体核心：

- `Candidate`（`file_hash + jd_id` 唯一）
- `UploadBatch`, `UploadItem`
- `CandidateSnapshot`
- `ResumeChunkVector`, `VectorIngestTask`
- `Contact`, `Education`, `Honor`, `Link`, `Experience`

规则抽取核心：

- `DefaultFactRules`, `RuleBasedFactChunkSelector`
- `FactExtractorDispatcher`
- `ContactExtractor`, `EducationExtractor`, `HonorExtractor`, `LinkExtractor`
- `Regex`, `LazyFeatures`, 各 rule 类

## 3.5 `infra`（外部能力）

- `infra.parser.TikaResumeParser`：Tika 解析，1MB 输出上限与截断标记。
- `infra.embedding.EmbeddingService`：WebClient 调 Python `/embed`。
- `infra.vector.VectorStoreService`：
  - save/search/delete/index 管理
  - pgvector 不可用时降级保护
  - 写并发信号量限流

## 3.6 `config` 与 `actuator`

- 线程池：
  - `UploadWorkerExecutorConfig`
  - `VectorIngestWorkerExecutorConfig`
- 启动期守卫：`VectorDimensionConsistencyValidator`
- 队列索引自修复：`UploadQueueIndexInitializer`
- 观测端点：
  - `UploadPerformanceEndpoint`
  - `VectorIngestPerformanceEndpoint`

## 4. 核心链路设计

## 4.1 上传处理链路（主链路）

1. `UploadController` 接收文件并写入 `storage`。
2. 写 `UploadBatch` + `UploadItem(QUEUED)`，并生成 `queue_job_key`。
3. `UploadQueueWorkerRunner` 拉起消费者。
4. `UploadQueueWorkerService.consumeQueuedItems()`：
   - claim queued items
   - 调用 `ResumeProcessService.process(...)`
   - 回写 `DONE/DUPLICATE/FAILED`
   - 刷新批次进度
   - 推送 `BatchStreamEvent`

### 4.1.1 一致性机制

- 租约：`queue_job_key` 防 stale 回写。
- 事务：worker 内通过 `TransactionTemplate` 分段执行。
- 进度：批次统计统一由 SQL 聚合计算。

## 4.2 向量入库链路（异步子链路）

1. `ResumeProcessService` 仅对 `EXPERIENCE/SKILL` 分块调用 `VectorIngestService.ingest()`。
2. `VectorIngestTask` 进入 `PENDING`。
3. `VectorIngestQueueWorkerService` claim 并处理：
   - `VectorStoreService.save(...)`
   - 成功置 `DONE`
   - 失败按 attempt 进入 `PENDING/FAILED`
4. 候选人所有任务无 inflight 且存在 DONE 后，推送 `VectorStatusStreamEvent(VECTOR_DONE)`。

## 4.3 搜索链路

`SearchController` 关键逻辑：

- 把 JD 文本 embedding 作为 query vector。
- 调 `VectorStoreService.search(...)` 检索 chunk。
- 按 candidate 聚合，计算 `EXPERIENCE/SKILL` 权重分。
- 支持 `onlyVectorReadyCandidates` 过滤。

## 5. 状态机设计

## 5.1 `UploadItemStatus`

枚举定义：

- `QUEUED`, `PROCESSING`, `DONE`, `DUPLICATE`, `FAILED`

现行主状态：

- 入队：`QUEUED`
- 消费：`PROCESSING`
- 终态：`DONE` / `DUPLICATE` / `FAILED`

## 5.2 `VectorIngestTaskStatus`

- `PENDING -> PROCESSING -> DONE`
- 失败：
  - 可恢复异常：回到 `PENDING`
  - 超过最大尝试：`FAILED`

## 6. 前端代码设计

前端是单页主组件模式（`App.vue`）：

- 本地状态：JD、候选人、语义分、筛选、分页、上传状态。
- 网络层：`fetch` 调用后端 REST。
- 实时层：`EventSource` 订阅 `candidate/vector` 两类 SSE 事件。
- 语义层：
  - `semanticMatching.js` 构建检索 payload 与权重建议
  - 排序以 `/api/search` 返回分数与顺序为准，前端只做展示与筛选

优点：

- 交互集中，迭代快。

代价：

- `App.vue` 体积大、职责多，后续建议按域拆分 composables 和子组件。

## 7. Python 与模型侧设计

## 7.1 `Qwen/embedding_service.py`

- FastAPI 统一服务，支持 embedding 与 generation。
- 懒加载模型，支持设备与 dtype 配置。
- 作为后端 `EmbeddingService` 的外部依赖。

## 7.2 `Qwen/dataGen.py` + `run_datagen_long.py`

- 简历数据集生成（多领域、多格式、可控随机）。
- 长任务编排：批次重试、状态恢复、最终产物合并。

## 7.3 `infra/vllm`

- `docker-compose.yml` 启动 LLM + Embedding + nginx gateway。
- gateway 统一 OpenAI 风格访问入口（`:8002`）。

## 8. 测试设计

后端测试覆盖维度：

- parser/embedding/vector 配置与异常处理
- repository 与事实抽取集成
- controller 集成测试（上传、搜索、JD、候选人）
- 完整流程测试（`FullPipelineIntegrationTest`）

前端测试：

- 语义工具函数行为与边界值。

## 9. 关键设计决策与权衡

1. DB 队列替代外部 MQ
- 优点：部署简单、事务一致性好。
- 风险：高吞吐时数据库热点与索引依赖更强。

2. 双队列拆分
- 优点：解析入库与向量入库解耦，避免互相阻塞。
- 风险：链路更长，需要更强可观测性与重试策略。

3. SSE + Snapshot
- 优点：前端可实时展示且支持断线后重建。
- 风险：连接管理与事件幂等需持续关注。

4. Flyway 单一 schema 真源
- 优点：`ddl-auto=validate` + 迁移脚本可保证环境一致性。
- 风险：迁移脚本评审与回滚要求更高。

## 10. 当前设计债与修复方案

1. 测试环境与生产 schema 治理路径仍有分叉（测试仍以 H2 `ddl-auto=update` + `flyway=false` 运行）。
修复方案：新增一套 `db/migration-h2` 测试迁移并启用 Flyway，或切换为 Testcontainers PostgreSQL 作为集成测试主路径，逐步下线 `ddl-auto=update`。

2. H2 下 `UploadQueueIndexInitializer` 的 partial index 语句持续产生 WARN（`WHERE storage_path IS NOT NULL` 兼容差异）。
修复方案：为索引初始化器增加数据库方言分支（H2 降级为普通索引，PostgreSQL 保留 partial index），并补充对应配置测试。

3. 兼容层仍在长期驻留（SSE 旧路径与上传状态别名映射）。
修复方案：增加兼容命中指标与日志，设定两次小版本观察窗口后移除旧路径和别名，仅保留规范路径与规范状态。

4. 前端 `App.vue` 仍为单文件大组件，状态管理与视图耦合偏高。
修复方案：按领域拆分 composables 与子组件（上传、搜索、语义排序、SSE），先抽离状态与副作用，再下沉 UI 组件，保持行为不变逐步替换。
