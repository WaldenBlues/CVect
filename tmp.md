先建立一个开发者心智模型

  这个项目本质上是 4 个子系统拼起来的：

  1. JD/上传管理
  2. 简历解析与结构化入库
  3. 向量化与语义检索
  4. SSE 驱动的前端实时看板

  后端不是“一个请求做完所有事”，而是把链路拆成两段异步流水线：

  上传文件 -> upload_items 队列 -> 候选人入库 -> vector_ingest_tasks 队列 -> 向量入库 -> 搜索可用

  这个拆分是你重新熟悉代码时最重要的主线。UploadController.java:89 ResumeProcessService.java:71 VectorIngestService.java:36

  ———

  1. 为什么用“数据库队列”而不是直接同步处理

  实现上，上传 API 只负责做三件事：

  - 校验 JD 和文件类型
  - 把文件写到 storage
  - 创建 UploadBatch 和 UploadItem，状态先置成 QUEUED

  真正的解析不在 Controller 里做，而是交给后台 worker 轮询数据库去消费。UploadController.java:105 UploadController.java:210

  这样做的目的很直接：

  - 上传请求尽快返回，避免大文件解析阻塞 HTTP
  - 失败可重试
  - 后续可以横向扩 worker，而不改业务模型
  - embedding 慢、模型挂掉，也不会把主上传链路拖死

  worker 是在应用启动时通过 @PostConstruct 拉起的常驻轮询线程，不依赖 @Scheduled 做主消费。UploadQueueWorkerRunner.java:49
  VectorIngestQueueWorkerRunner.java:45

  ———

  2. 上传队列的关键实现要点

  这块代码你要重点记住 4 个词：claim、lease、recovery、progress aggregation。

  2.1 claim 机制

  PostgreSQL 下，upload_items 用 FOR UPDATE SKIP LOCKED 批量 claim，避免多个 worker 抢同一条记录。UploadItemJpaRepository.java:87

  如果数据库不是 PostgreSQL，就退回 portable 方案：先查一批 QUEUED，再逐条乐观更新状态到 PROCESSING。UploadQueueWorkerService.java:97

  2.2 lease 机制

  每个 UploadItem 有一个 queue_job_key，本质上是消费租约令牌。worker 成功 claim 后，后续回写 DONE/FAILED 时必须带上同一个 key，才能更新成
  功。UploadItemJpaRepository.java:159 UploadItemJpaRepository.java:180

  这解决的是 stale worker 问题：如果某个旧 worker 卡住很久，后来恢复执行，它不能覆盖新 worker 的结果。

  UploadQueueJobKeyGenerator 只是生成一个不透明 token，不承载业务语义。UploadQueueJobKeyGenerator.java:10

  2.3 stale recovery

  如果某条任务长时间停留在 PROCESSING，维护逻辑会把它恢复成 QUEUED，并换一把新的 job key，让别的 worker 接手。
  UploadQueueWorkerService.java:154

  这说明作者把“worker 异常退出、线程死掉、服务重启”作为正常场景处理，而不是当成人工修复场景。

  2.4 批次进度聚合

  UploadBatch 的 processed_files 不是在 Java 里逐个累加，而是通过 SQL 原子聚合 upload_items 计算，避免并发更新时的计数漂移。
  UploadBatchJpaRepository.java:20

  这个点很重要：设计上把“批次进度”当成派生状态，而不是主状态。

  ———

  3. 解析链路的职责拆分

  上传 worker 真正处理一份简历时，核心入口是 ResumeProcessService.process(...)。ResumeProcessService.java:71

  它的职责顺序很清楚：

  - 读完整文件字节并算 sha256
  - 解析文件内容
  - 标准化文本
  - 提取姓名
  - 分块
  - 候选人幂等入库
  - 按 chunk 做事实抽取
  - 只把可向量化 chunk 投递到第二条队列
  - 发布候选人事件

  3.1 幂等不是基于文件名，而是文件内容 hash

  候选人实体在数据库层有 (file_hash, jd_id) 唯一约束，所以同一个简历在同一个 JD 下不会重复建 candidate。Candidate.java:14
  V2__candidate_file_hash_jd_unique.sql:19

  所以 duplicate 的判断是内容级别，而不是上传文件名级别。

  3.2 Parser 只负责“尽可能提取文本”

  Tika parser 做了两个明确约束：

  - 最多提取 1MB 字符
  - 超限时标记 truncated=true，而不是直接失败

  另外，禁用了内嵌文档解析和 PDF OCR，说明作者优先的是稳定性和吞吐，不追求复杂文档的极限抽取率。TikaResumeParser.java:27
  TikaResumeParser.java:72

  3.3 标准化和分块是规则驱动，不是 LLM 驱动

  文本标准化先做换行、空行压缩、噪声链接移除、页码移除。DefaultResumeTextNormalizer.java:8

  然后 DefaultChunkerService 根据章节标题、关键词和局部信号推断 CONTACT / EDUCATION / EXPERIENCE / SKILL / HONOR / LINK 等 chunk 类型。
  DefaultChunkerService.java:38

  这个设计的优点是：

  - 结果可预期
  - 好调试
  - 不依赖模型推理成本

  代价是对格式变化更敏感。

  3.4 事实抽取是“类型决定持久化方式”

  ResumeFactService 不自己识别复杂事实结构，它更像一个 orchestrator：

  - dispatcher.extractAll(chunk) 先把 chunk 提取成若干字符串
  - 再根据 chunk.getType() 决定落哪张表

  FactRepository 只是把多张表的 JPA 写入封成一个 facade，减少 service 直接依赖多个 repository。ResumeFactService.java:33
  FactRepository.java:17

  3.5 向量化只针对 EXPERIENCE / SKILL

  这是非常关键的业务取舍。不是所有 chunk 都向量化，只把最适合语义匹配的两类内容送去生成向量。ResumeProcessService.java:177

  这样做的实际效果是：

  - 降低 embedding 成本
  - 避免联系方式、荣誉这种噪声文本干扰召回
  - 搜索结果更接近“经验和技能匹配”

  ———

  4. 为什么又拆出第二条“向量入库队列”

  如果你只记一句话：作者故意把“候选人入库成功”和“向量可搜索”分开定义了。

  VectorIngestService.ingest(...) 并不立即调用 embedding 服务，而是先写 vector_ingest_tasks(PENDING)。VectorIngestService.java:36

  然后另一个 worker 消费这些任务，执行：

  - 调 embedding 服务
  - 写 resume_chunks
  - 把任务改成 DONE
  - 如果一个 candidate 的所有向量任务都结束，就发 VECTOR_DONE SSE 事件

  见 VectorIngestQueueWorkerService.java:158 和 VectorIngestQueueWorkerService.java:213

  这样拆的核心收益：

  - embedding 服务故障不会让简历主入库失败
  - 搜索 readiness 可以独立跟踪
  - 可以对向量队列单独做限流、重试、积压保护

  VectorIngestService 里还有一个保护：当 PENDING/PROCESSING 太多时，不再继续 enqueue，避免模型服务被打爆。VectorIngestService.java:43

  ———

  5. 向量存储实现最值得注意的几个细节

  VectorStoreService 是整个语义检索的技术核心。VectorStoreService.java:27

  5.1 Java 不本地推 embedding，而是 HTTP 调 Python

  EmbeddingService 用 WebClient 调 Qwen/embedding_service.py 暴露的 /embed。如果 /embed 404，还能 fallback 到 OpenAI 兼容的 /v1/
  embeddings。EmbeddingService.java:53 EmbeddingService.java:61 embedding_service.py:1

  这说明模型服务边界是被刻意做成可替换的。

  5.2 向量维度有启动期守卫

  启动时会检查 app.embedding.dimension 和 app.vector.dimension 必须一致，否则直接 fail fast。VectorDimensionConsistencyValidator.java:24

  这类守卫很实用，因为维度错了不是运行时偶发 bug，而是全链路配置错误。

  5.3 resume_chunks.embedding 在实体层是 float[]，数据库层为了兼容测试以 TEXT 处理

  这是个很有实现味道的点。ResumeChunkVector 里 embedding 字段是 float[]，注释明确写了 H2 兼容时用 TEXT。ResumeChunkVector.java:42

  搜索时不是直接拿原生 vector 列，而是把 TEXT 里的 {} 格式转成 pgvector 的 [] 格式，再 cast 成 vector(n) 计算距离。
  VectorStoreService.java:318

  这个实现说明作者在“生产能力”和“本地/H2 测试兼容”之间做了折中。

  5.4 写向量时做了并发限流

  save(...) 先拿 semaphore permit，超时拿不到就直接跳过写入并告警。VectorStoreService.java:73

  这不是完美一致性优先，而是“系统整体不被模型写入拖垮”优先。

  5.5 启动时自动检查 pgvector 和索引兼容性

  - 会尝试 CREATE EXTENSION IF NOT EXISTS vector
  - 会检查 HNSW index 是否和当前维度一致
  - 不一致就重建索引

  见 VectorStoreService.java:257 和 VectorStoreService.java:287

  这说明项目是把“本地反复改配置/重启”场景考虑进去的。

  ———

  6. 搜索为什么是“先搜 chunk，再聚合 candidate”

  搜索 API 很薄，主要逻辑在 SemanticSearchService.search(...)。SearchController.java:44 SemanticSearchService.java:51

  执行过程是：

  - 先把 JD 文本转 query embedding
  - 用向量检索召回 chunk
  - 再按 candidateId 聚合
  - 分别计算 EXPERIENCE 和 SKILL 的最大匹配分
  - 按配置权重算总分并排序

  这里作者没有做“candidate 整体向量”，而是保留 chunk 粒度，优点是：

  - 可解释性更强，能返回命中的 chunk 内容
  - Experience 和 Skill 可以分别加权
  - 适合招聘场景的“简历部分命中”而不是整体命中

  代码在 SemanticSearchService.java:108

  缓存设计也很实用：

  - JD 文本 embedding 做缓存
  - 完整搜索结果也做短 TTL 缓存

  因为招聘场景里，一个 JD 会被多次重复搜索，这是高命中场景。SearchQueryEmbeddingCacheService.java:17 CacheConfig.java:27
  application.yml:92

  ———

  7. SSE 和快照设计为什么很关键

  这部分是很多人第一次读代码时会忽略的，但它其实是前后端交互稳定性的关键。

  7.1 SSE 只负责“推事件”，不负责“补全列表”

  CandidateStreamService 维护 SseEmitter 列表，往所有订阅者广播两类事件：

  - candidate
  - vector

  并且每 20 秒发一次 ping 保活。CandidateStreamService.java:37 CandidateStreamService.java:55

  这是典型的“轻实时通道”设计。

  7.2 候选人列表不是实时 join 多表，而是用 candidate_snapshots 做读模型

  CandidateSnapshotService.build(...) 会把 Candidate、Contact、Education、Honor、Link 聚合成一个 CandidateStreamEvent，同时 upsert 到
  candidate_snapshots。CandidateSnapshotService.java:82 CandidateSnapshotService.java:140

  这个设计的意义是：

  - SSE 推送和列表查询用的是同一种事件形态
  - 前端读模型稳定，不依赖大量 join
  - 历史候选人列表可以快速恢复

  也就是说，candidate_snapshots 本质上是一个为 UI 服务的投影表。

  7.3 /api/candidates 会补 noVectorChunk 状态

  候选人列表除了基础信息，还会查哪些 candidate 已经有向量 chunk，没有的话给前端一个 noVectorChunk=true 标志。CandidateController.java:53

  这让前端能够区分：

  - 候选人已经入库，但还没向量化完成
  - 候选人已可参与语义排序

  ———

  8. 前端的实现思路

  前端是一个非常直接的单页控制台，主状态基本都集中在 App.vue:293。

  你可以把它理解成 3 层状态：

  - JD 状态
  - 候选人列表状态
  - 语义排序状态

  8.1 SSE 到前端的落地

  前端在 connect() 里建立 EventSource，收到：

  - candidate 事件就更新本地候选人数组
  - vector 事件就把对应 candidate 的 noVectorChunk 设成 false，并触发一次语义重排刷新

  见 App.vue:586

  这说明前端没有自己维护复杂消息队列，只做增量 merge。

  8.2 语义排序被抽成 composable

  useSemanticMatching 负责：

  - 根据 JD 构造 /api/search payload
  - 维护 semanticScoreMap 和 semanticRankMap
  - 自动根据 JD 内容建议 Experience/Skill 权重
  - 防抖刷新搜索

  见 useSemanticMatching.js:4 和 semanticMatching.js:13

  这里的设计点是：排序逻辑以后端分数为准，前端只负责展示、映射和兜底补 0 分。

  ———

  9. 配置、迁移和运行期守卫

  这部分帮助你理解项目为什么“本地能反复跑起来”。

  9.1 配置是分层的

  application.yml 里明显分成：

  - app.embedding
  - app.vector
  - app.upload
  - app.cache

  说明作者把“模型服务、向量库、上传队列、缓存”作为四个独立可调模块来管理。application.yml:36

  9.2 schema 以 Flyway 为准，JPA 只做 validate

  spring.jpa.hibernate.ddl-auto=validate，意味着运行时不允许 Hibernate 偷偷改表，数据库 schema 由 Flyway 统一管理。application.yml:17

  9.3 迁移策略是“marker + convergence”

  - V1 只是 baseline marker
  - V2 修正候选人唯一约束
  - V3 用 IF NOT EXISTS 把历史环境收敛到当前 schema

  见 V1__baseline.sql:1 V2__candidate_file_hash_jd_unique.sql:1 V3__schema_and_upload_status_convergence.sql:1

  这说明当前仓库经历过多次 schema 演化，作者专门处理了历史环境兼容问题。

  ———

  10. 你重新读代码时，最该盯住的“设计不变量”

  如果你只想最快找回感觉，就记这几个不变量：

  - UploadController 只入队，不做重处理。
  - ResumeProcessService 是简历主编排中心。
  - upload_items 解决“文件处理”问题，vector_ingest_tasks 解决“向量写入”问题，两者不能混。
  - 候选人是否存在由 file_hash + jd_id 决定，不由文件名决定。
  - 语义搜索基于 chunk，不基于 candidate 全文。
  - SSE 是增量通知通道，candidate_snapshots 才是读模型。
  - embedding 是外部能力，后端只做编排与校验，不直接做模型推理。

  ———

  推荐你的复习顺序

  按下面顺序读，效率最高：

  1. UploadController.java:89
  2. UploadQueueWorkerService.java:83
  3. ResumeProcessService.java:71
  4. DefaultChunkerService.java:38
  5. ResumeFactService.java:33
  6. VectorIngestService.java:36
  7. VectorIngestQueueWorkerService.java:158
  8. VectorStoreService.java:63
  9. SemanticSearchService.java:51
  10. CandidateSnapshotService.java:82
  11. App.vue:586
  12. useSemanticMatching.js:129