# CVect 按类拆解复习手册

这份文档面向开发者复习代码，重点不是“功能列表”，而是“每个关键类在系统里到底负责什么、接什么、产出什么、为什么这么设计”。

## 1. 后端主线速记

系统主链路分两段：

1. 上传简历 -> `upload_items` 队列 -> 解析/抽取/候选人入库
2. 向量任务 -> `vector_ingest_tasks` 队列 -> embedding -> `resume_chunks` -> 语义检索可用

核心原因：

- 上传入库和向量化解耦，避免模型服务拖慢主链路
- 数据库队列便于本地开发与恢复，不依赖额外 MQ
- 前端通过 SSE 看到“候选人入库完成”和“向量完成”两个阶段性状态

---

## 2. Web / Controller 层

### `UploadController`

职责：

- 接收单/多文件上传和 ZIP 上传
- 校验 JD、文件类型、大小与并发上限
- 将文件落到本地 `storage`
- 创建 `UploadBatch` 和 `UploadItem`
- 发布批次 SSE 事件

输入：

- `jdId`
- `MultipartFile[]` 或 `zipFile`

输出：

- `BatchUploadResponse`
- `ZipUploadResponse`
- HTTP `429` 表示当前上传队列过载

依赖：

- `JobDescriptionJpaRepository`
- `UploadBatchJpaRepository`
- `UploadItemJpaRepository`
- `BatchStreamService`
- `UploadQueueJobKeyGenerator`

实现要点：

- Controller 只负责“接收 + 入队”，不直接做简历解析
- 上传时先做 inflight reservation，避免瞬时塞爆队列
- ZIP 每个 entry 单独校验，失败项会落成 `FAILED`
- 入队时状态直接写成 `QUEUED`

常见追问：

- 为什么这里不直接调用 `ResumeProcessService`？
  因为解析链路耗时长且会依赖 Tika/embedding，放在请求线程里会拖慢上传接口，也不利于失败重试。
- 为什么要有 `UploadBatch`？
  因为前端需要批次级进度展示和重试失败项，单个 `UploadItem` 不够表达整个上传会话。

### `JobDescriptionController`

职责：

- JD 的增删改查
- 删除 JD 时清理相关候选人、事实数据、快照和向量数据

输入：

- `JobDescriptionRequest`
- `id`

输出：

- `JobDescriptionSummary`
- 删除成功返回 `204`

依赖：

- `JobDescriptionJpaRepository`
- `CandidateJpaRepository`
- 各类事实表 repository
- `CandidateSnapshotJpaRepository`
- `UploadBatchJpaRepository`
- `UploadItemJpaRepository`
- `VectorStoreService`

实现要点：

- 删除 JD 前检查是否已有候选人；当前实现有候选人时返回 `409`
- 控制器里有明显的“聚合删除”逻辑，偏向实用主义，不是严格 DDD

常见追问：

- 为什么删除逻辑这么重？
  因为 JD 是这套系统的业务边界之一，JD 删除意味着其下简历与检索数据都应一起清理。

### `CandidateController`

职责：

- 按 JD 查询候选人列表
- 更新招聘状态
- 补充 `noVectorChunk` 给前端使用

输入：

- `jdId`
- `UpdateRecruitmentStatusRequest`

输出：

- `CandidateListItem`
- 更新后的 `CandidateStreamEvent`

依赖：

- `CandidateJpaRepository`
- `CandidateSnapshotService`
- `ResumeChunkVectorJpaRepository`

实现要点：

- 列表读取优先走 `candidate_snapshots`
- 如果快照缺失，再即时构建一次快照
- `noVectorChunk` 不是实体字段，而是读时动态算出来的 UI 状态

常见追问：

- 为什么候选人列表不直接多表 join？
  因为前端高频读列表，快照读模型更稳定，也更适合 SSE 同构数据输出。

### `CandidateStreamController`

职责：

- 暴露 `GET /api/candidates/stream`
- 返回 `SseEmitter`

输入：

- 无业务参数

输出：

- SSE 流

依赖：

- `CandidateStreamService`

实现要点：

- 非常薄，只负责把流能力暴露出去

常见追问：

- 为什么用 SSE 而不是 WebSocket？
  因为这里只有服务端单向推送，没有复杂双向通信需求，SSE 更轻。

### `SearchController`

职责：

- 接收语义搜索请求
- 统计请求级耗时指标
- 提供创建 HNSW 索引的管理接口

输入：

- `SearchRequest`

输出：

- `SearchResponse`

依赖：

- `SemanticSearchService`
- `VectorStoreService`

实现要点：

- Controller 本身不做排序计算，真正逻辑在 service
- `SearchRequest` 内部会 clamp `topK`

常见追问：

- 为什么还保留 `create-index`？
  因为索引重建在本地和测试环境中是显式运维动作，保留管理入口便于操作。

---

## 3. Service 层主编排

### `ResumeProcessService`

职责：

- 简历处理总编排入口
- 负责 parse -> normalize -> name extract -> chunk -> fact save -> candidate persist -> vector ingest enqueue
- 发布候选人 SSE 事件

输入：

- `InputStream`
- `contentType`
- `sourceFileName`
- `fileSizeBytes`
- `jdId`

输出：

- `ProcessResult(candidateId, chunks, duplicated, fileHash)`

依赖：

- `ResumeParser`
- `ResumeTextNormalizer`
- `NameExtractor`
- `ChunkerService`
- `ResumeFactService`
- `CandidateJpaRepository`
- `JobDescriptionJpaRepository`
- `CandidateSnapshotService`
- `CandidateStreamService`
- `VectorIngestService`

实现要点：

- 先读取完整字节并算 `sha256`，为幂等去重做准备
- 候选人是否重复由 `file_hash + jd_id` 决定，不看文件名
- 事实抽取失败不会让整体流程失败，按 chunk 降级处理
- 只有 `EXPERIENCE` 和 `SKILL` chunk 进入向量队列

常见追问：

- 为什么 duplicate 还要更新姓名/JD？
  因为重复文件也可能补齐历史数据，避免老数据长期不完整。
- 为什么不把所有 chunk 都向量化？
  联系方式、荣誉等 chunk 对语义匹配收益小，反而增加成本和噪声。

### `UploadQueueWorkerService`

职责：

- 消费 `upload_items`
- claim 队列任务
- 调用 `ResumeProcessService`
- 回写最终状态
- 做 stale recovery
- 刷新批次进度并发送 SSE

输入：

- 内部轮询数据库，无外部 API 输入

输出：

- 返回本轮处理的任务数量

依赖：

- `UploadItemJpaRepository`
- `UploadBatchJpaRepository`
- `ResumeProcessService`
- `BatchStreamService`
- `JdbcTemplate`
- `PlatformTransactionManager`

实现要点：

- PostgreSQL 下走 `FOR UPDATE SKIP LOCKED`
- 非 PostgreSQL 环境退回 portable claim
- 使用 `queue_job_key` 做消费租约，防 stale worker 回写覆盖
- 成功与失败状态更新都必须匹配当前租约
- stale 的 `PROCESSING` 项会恢复回 `QUEUED`

常见追问：

- 为什么这里用数据库队列而不是 MQ？
  当前是单体应用，数据库队列部署简单、可观测性强、足够支撑现阶段吞吐。
- 为什么用 `TransactionTemplate` 而不是把整个方法加 `@Transactional`？
  因为 worker 需要分段事务，claim、处理、回写和恢复最好分开，避免大事务拖垮轮询。

### `UploadQueueWorkerRunner`

职责：

- 启动上传 worker 线程
- 常驻轮询 `UploadQueueWorkerService`
- 优雅停止

输入：

- 配置项：`consumer-count`、`initial-delay-ms`、`idle-sleep-ms`

输出：

- 无直接业务输出

依赖：

- `UploadQueueWorkerService`
- `TaskExecutor uploadWorkerExecutor`

实现要点：

- `@PostConstruct` 拉起多个消费者线程
- 处理到任务就立刻继续轮询；空闲才 sleep

常见追问：

- 为什么不用 `@Scheduled`？
  因为这里需要自定义线程池、多个消费者、持续轮询和更细的关闭控制。

### `VectorIngestService`

职责：

- 负责把可向量化 chunk 封装为 `VectorIngestTask`
- 控制向量队列是否过载

输入：

- `candidateId`
- `chunkType`
- `content`

输出：

- 无返回值，副作用是入队

依赖：

- `VectorIngestTaskJpaRepository`

实现要点：

- 向量或 worker 被禁用时直接跳过
- 当 `PENDING + PROCESSING` 太多时直接放弃入队，避免堆积失控

常见追问：

- 为什么队列满了直接 skip 而不是阻塞？
  当前更偏向保护系统整体稳定性，避免模型侧故障把主链路放大成全局阻塞。

### `VectorIngestQueueWorkerService`

职责：

- 消费 `vector_ingest_tasks`
- 调用 `VectorStoreService.save`
- 回写 `DONE / PENDING / FAILED`
- 对 embedding 连接类故障做重试
- 在 candidate 向量全部完成后发 `VECTOR_DONE`

输入：

- 内部轮询数据库

输出：

- 返回本轮处理的任务数

依赖：

- `VectorIngestTaskJpaRepository`
- `VectorStoreService`
- `CandidateSnapshotService`
- `CandidateStreamService`
- `JdbcTemplate`
- `PlatformTransactionManager`

实现要点：

- 与上传队列同样支持 PostgreSQL 原生 claim 和 portable fallback
- 可恢复错误会回到 `PENDING`
- 到达 `maxAttempts` 后标记 `FAILED`
- `publishVectorDoneIfReady` 会检查该 candidate 是否已无 inflight 任务且至少有一个 `DONE`

常见追问：

- 为什么向量任务不做 job key 租约？
  这里状态机更简单，claim 和完成通过 `status=PROCESSING` 条件回写即可控制并发覆盖。

### `VectorIngestQueueWorkerRunner`

职责：

- 拉起向量 worker 线程
- 常驻轮询向量任务

输入：

- 配置项：消费者数、初始延迟、空闲轮询间隔

输出：

- 无

依赖：

- `VectorIngestQueueWorkerService`
- `TaskExecutor vectorIngestWorkerExecutor`

实现要点：

- 逻辑与上传 runner 对称
- 两种 worker 的对称设计降低了理解成本

常见追问：

- 为什么两个 runner 分开？
  因为两个队列的负载特性、失败模型和配置不同，拆开更便于调优和隔离。

### `ResumeFactService`

职责：

- 从 chunk 中抽取事实并写入结构化表

输入：

- `candidateId`
- `ResumeChunk`

输出：

- 无返回值，副作用是写入 `contacts / educations / honors / links`

依赖：

- `FactExtractorDispatcher`
- `FactRepository`

实现要点：

- 先统一抽取字符串结果，再按 `ChunkType` 分发到具体表
- `EXPERIENCE / SKILL` 当前不在这里持久化为结构化表

常见追问：

- 为什么这里不直接落 experience 表？
  当前业务主价值在向量检索，不在于把经验内容完全结构化。

### `CandidateSnapshotService`

职责：

- 构建候选人读模型快照
- 生成 SSE 输出使用的 `CandidateStreamEvent`
- 将事件 upsert 到 `candidate_snapshots`

输入：

- `candidateId`
- 可选 `status`

输出：

- `CandidateStreamEvent`

依赖：

- `CandidateJpaRepository`
- `CandidateSnapshotJpaRepository`
- `ContactJpaRepository`
- `EducationJpaRepository`
- `HonorJpaRepository`
- `LinkJpaRepository`
- `ObjectMapper`

实现要点：

- 这个类是“聚合读模型装配器”
- 统一了列表读取和实时推送的数据形态
- 各列表字段序列化进 JSON 文本列，牺牲一部分规范化，换取读性能与简单性

常见追问：

- 为什么要有 `candidate_snapshots` 这张表？
  因为候选人列表和 SSE 都需要聚合后的读模型，实时多表拼装成本高且不稳定。

### `SemanticSearchService`

职责：

- 处理语义搜索主逻辑
- 获取 query embedding
- 从向量表召回 chunk
- 按 candidate 聚合和加权排序
- 可选过滤掉向量未完成的候选人

输入：

- `SearchController.SearchRequest`

输出：

- `SearchController.SearchResponse`

依赖：

- `VectorStoreService`
- `SearchQueryEmbeddingCacheService`
- `VectorIngestTaskJpaRepository`
- `MeterRegistry`

实现要点：

- `topK` 会先扩大成 chunk 召回量，再聚合成 candidate
- Experience 和 Skill 分别取最大分，再按权重加权
- 如果加权分无意义，就退回整体最大分
- 方法本身有响应缓存

常见追问：

- 为什么先 oversample chunk？
  因为同一个 candidate 可能贡献多个 chunk，不先扩大召回量，聚合后 candidate 覆盖度会不足。
- 为什么聚合时取最大分而不是平均分？
  当前更偏向“候选人有强命中片段即可前排”，符合招聘筛选的直觉。

### `SearchQueryEmbeddingCacheService`

职责：

- 缓存 JD 文本对应的 query embedding

输入：

- `jobDescription`

输出：

- `float[] embedding`

依赖：

- `EmbeddingService`

实现要点：

- 使用 `@Cacheable(sync = true)` 避免并发下重复计算同一份 JD embedding

常见追问：

- 为什么单独拆这个类？
  因为 query embedding 缓存是独立关注点，便于复用和单测。

---

## 4. Infra 层

### `TikaResumeParser`

职责：

- 从 PDF / DOC / DOCX / TXT / MD 中提取纯文本

输入：

- `InputStream`
- `contentType`

输出：

- `ParseResult(content, truncated, detectedType, charCount)`

依赖：

- Apache Tika

实现要点：

- 最多提取 1MB 字符
- 超限时 `truncated=true`
- 关闭 embedded document 解析
- PDF 不做 OCR

常见追问：

- 为什么不做 OCR？
  OCR 成本高且错误率不可控，当前项目更偏文本型简历处理。

### `DefaultResumeTextNormalizer`

职责：

- 对解析后的文本做轻量清洗

输入：

- 原始文本

输出：

- 规范化文本

依赖：

- 无外部依赖

实现要点：

- 统一换行
- 压缩空行
- 去掉大部分非代码仓库链接
- 去掉页码噪声

常见追问：

- 为什么规则这么少？
  因为它的目标是给 chunker 提供更稳定输入，而不是做复杂 NLP 预处理。

### `NameExtractor`

职责：

- 从规范化文本中尽量抽取候选人姓名

输入：

- 规范化文本

输出：

- 姓名字符串

依赖：

- `Regex`

实现要点：

- 多规则回退：`姓名:` 标签、邮箱同行、电话同行、邻近行、首行短文本
- 同时兼顾中英文姓名
- 会主动规避“项目/经历/技能”等块标题误判

常见追问：

- 为什么不用 NER 模型？
  名字抽取在简历场景里规则命中率已经较高，规则方案更轻、更稳定。

### `EmbeddingService`

职责：

- 调用外部 Python embedding 服务
- 校验返回向量维度
- 必要时 fallback 到 OpenAI 兼容 embeddings 接口

输入：

- `text`
- `List<String>`

输出：

- `float[]`
- `List<float[]>`

依赖：

- `WebClient`
- `EmbeddingConfig`

实现要点：

- Java 不本地跑模型，统一走 HTTP
- 404 时会从 `/embed` 自动推导 `/v1/embeddings` 兼容接口
- 响应数量和维度都要校验

常见追问：

- 为什么 embedding 单独放 Python 服务？
  Python 模型生态成熟，Java 端保留业务编排与校验职责即可。

### `VectorStoreService`

职责：

- 保存向量化 chunk
- 在 `resume_chunks` 上执行向量检索
- 管理 HNSW 索引
- 检查 pgvector 可用性与维度兼容

输入：

- `save(candidateId, chunkType, content)`
- `search(queryEmbedding, topK, chunkTypes...)`

输出：

- `boolean persisted`
- `List<SearchResult>`

依赖：

- `JdbcTemplate`
- `EntityManager`
- `EmbeddingService`
- `VectorStoreConfig`

实现要点：

- 启动时尝试启用 `pgvector`
- 写入前用 semaphore 做并发限流
- 存储和搜索时都校验维度
- 搜索走原生 SQL，而不是 JPA
- 索引维度变更时会尝试自动重建

常见追问：

- 为什么搜索不用 JPA？
  向量距离计算、cast、opclass、HNSW 等能力更适合直接写 SQL。
- 为什么 `save` 里自己生成 embedding？
  因为向量任务的职责就是把“原始 chunk 文本”落成可检索向量记录。

---

## 5. Repository 层关键理解

### `UploadItemJpaRepository`

职责：

- 上传队列的数据访问核心

最关键的方法：

- `claimNextQueuedBatch`
- `claimQueuedItemById`
- `recoverStaleProcessingLease`
- `completeProcessingSuccess`
- `completeProcessingFailure`

实现要点：

- 这个 repository 实际承载了上传队列状态机的绝大部分原子 SQL
- `queue_job_key` 是它最重要的并发保护字段

常见追问：

- 为什么这么多原子 update？
  因为队列消费的关键不是“查对象再 save”，而是并发场景下精确控制状态转换。

### `VectorIngestTaskJpaRepository`

职责：

- 向量任务队列的数据访问核心

最关键的方法：

- `claimNextPendingBatch`
- `recoverStaleProcessing`
- `completeSuccess`
- `completeFailure`
- `findCandidateIdsByStatusIn`

实现要点：

- 除了消费状态更新，还负责支撑“候选人是否向量 ready”的查询

常见追问：

- 为什么候选人 ready 判断依赖任务表，而不是只看 `resume_chunks`？
  因为只看结果表无法知道是否仍有未完成任务在路上。

### `UploadBatchJpaRepository`

职责：

- 批次进度聚合与基础 CRUD

最关键的方法：

- `refreshProgressFromItems`

实现要点：

- 批次状态不是靠 Java 代码计数，而是靠 SQL 聚合得出派生状态

常见追问：

- 为什么 `DONE` 由 SQL 判断？
  因为这是一种并发下更稳定的最终一致性聚合方式。

### `FactRepository`

职责：

- 将多张事实表写入封成 facade

输入：

- `candidateId`
- 事实数据

输出：

- 无

依赖：

- 各类 JPA repository

实现要点：

- 目的是降低 service 对底层多表的耦合，不是为了抽象复杂领域模型

---

## 6. Model / Entity 层重点

### `Candidate`

职责：

- 候选人主实体，只存解析元数据和招聘状态

关键字段：

- `fileHash`
- `jd`
- `name`
- `contentType`
- `fileSizeBytes`
- `parsedCharCount`
- `truncated`
- `recruitmentStatus`

实现要点：

- `(file_hash, jd_id)` 唯一约束定义幂等边界
- 不直接存 EXPERIENCE/SKILL 明细

常见追问：

- 为什么 candidate 不承载所有结构化数据？
  因为联系方式、教育、荣誉等天然是一对多，拆表更清晰。

### `UploadItem`

职责：

- 代表一个上传文件处理任务

关键字段：

- `status`
- `storagePath`
- `queueJobKey`
- `candidateId`
- `errorMessage`
- `attempt`

实现要点：

- 这是“文件处理队列记录”，不是简单附件记录

### `VectorIngestTask`

职责：

- 代表一个待向量化 chunk 的任务

关键字段：

- `candidateId`
- `chunkType`
- `content`
- `status`
- `attempt`
- `errorMessage`

实现要点：

- 一个 candidate 可以拆成多条向量任务
- 这是异步向量化能力的边界模型

### `ResumeChunkVector`

职责：

- 存储已经完成 embedding 的 chunk

关键字段：

- `candidateId`
- `chunkType`
- `content`
- `embedding`

实现要点：

- 这张表是语义检索真正的召回源

---

## 7. 前端关键模块

### `App.vue`

职责：

- JD 管理
- 上传入口
- 候选人列表与详情
- SSE 连接管理
- 招聘状态修改
- 语义排序展示

输入：

- 用户交互
- `/api/*`
- `/api/candidates/stream`

输出：

- 页面状态与交互反馈

依赖：

- `SemanticPanel`
- `useSemanticMatching`

实现要点：

- 是一个“大组件 + 多状态”的实现
- SSE 到来后做本地数组 merge，而不是完全重刷
- 向量事件到来后会刷新向量标记和语义排序

常见追问：

- 为什么不进一步拆组件？
  当前版本更偏快速迭代，后续如继续演进，最自然的方向是把 JD、SSE、候选人列表、招聘状态、语义搜索拆成独立 composable/组件。

### `useSemanticMatching`

职责：

- 管理前端语义排序状态
- 调 `/api/search`
- 维护分数映射和名次映射
- 自动调权重

输入：

- `events`
- `selectedJdId`
- `selectedJd`

输出：

- `semanticScoreMap`
- `semanticRankMap`
- `semanticLoading`
- `semanticMessage`

依赖：

- `semanticMatching.js`

实现要点：

- 通过 request sequence 避免旧请求覆盖新请求
- 没命中的已向量化候选人会补一个 0 分，保证 UI 不长期灰掉

常见追问：

- 为什么前端也参与权重逻辑？
  因为权重调优是交互层能力，排序计算仍以后端返回分数为准。

### `semanticMatching.js`

职责：

- 构造搜索 payload
- 生成 rank/score map
- 根据 JD 文本给出经验/技能权重建议

实现要点：

- 这里只做轻量启发式，不做复杂语义逻辑

---

## 8. 复习时最容易被问到的设计点

### 为什么是两条队列？

- 上传解析和向量化的耗时、依赖和失败模型不同
- embedding 服务慢或挂掉时，不应影响候选人主入库

### 为什么用数据库队列？

- 部署简单
- 事务边界自然
- 本地开发和测试成本低
- 当前吞吐阶段足够

### 为什么有 `candidate_snapshots`？

- 支撑列表页和 SSE 的统一读模型
- 避免高频读取时多表拼装

### 为什么只向量化 Experience/Skill？

- 这两类文本最有招聘匹配价值
- 控制 embedding 成本
- 降低噪声 chunk 干扰排序

### 搜索为什么按 chunk 检索再聚合？

- 更可解释
- 支持不同 chunk type 加权
- 更适合简历局部命中的业务语义

---

## 9. 建议你的源码复习顺序

1. `UploadController`
2. `UploadQueueWorkerService`
3. `ResumeProcessService`
4. `DefaultResumeTextNormalizer`
5. `NameExtractor`
6. `DefaultChunkerService`
7. `ResumeFactService`
8. `VectorIngestService`
9. `VectorIngestQueueWorkerService`
10. `EmbeddingService`
11. `VectorStoreService`
12. `SemanticSearchService`
13. `CandidateSnapshotService`
14. `CandidateController`
15. `App.vue`
16. `useSemanticMatching`

按这个顺序读，基本就是沿着真实业务链路复盘代码。
