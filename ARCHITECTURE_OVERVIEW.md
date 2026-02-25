# CVect 项目宏观架构概览

> 说明：本文件仅描述后端与系统层面的整体架构，不包含前端实现细节。

## 目标与边界
CVect 是一个简历解析与候选人入库系统，采用规则化抽取 + （规划中的）向量检索架构。核心目标是：
- 将简历解析为结构化候选人数据
- 支持按 JD（岗位）进行候选人分桶与管理
- 未来将 EXPERIENCE / SKILL 进入向量库以支持语义检索

## 运行时拓扑
- **解析服务（Java 17 + Spring Boot）**：核心业务入口
- **关系数据库（PostgreSQL）**：持久化候选人基础信息与事实字段
- **向量服务（规划）**：EXPERIENCE / SKILL 的向量化与检索
- **对象存储（本地 storage/）**：批量上传文件落盘

## 核心模块分层

### 1) API 层（Web）
- `ResumeController`：单文件解析与入库入口
- `UploadController`：多文件 / ZIP 批量上传入口
- `JobDescriptionController`：JD（岗位）管理
- `CandidateController`：按 JD 查询候选人列表
- `CandidateStreamController`：SSE 候选人入库事件推送
- `BatchSseController`：按批次 SSE 推送（批量上传进度）

### 2) Service 层（业务编排）
- `ResumeProcessService`
  - 解析 -> 规范化 -> 分块 -> 抽取事实
  - 文件哈希去重，保持幂等
  - JD 绑定仅在首次入库时设置，避免重复上传导致归属变化
- `ResumeFactService`
  - 将分块交给规则抽取器，写入 RDBMS
  - EXPERIENCE / SKILL 按约定不入库（向量化专用）
- `CandidateSnapshotService`
  - 聚合候选人基础信息 + 联系方式/教育/荣誉/链接
  - 生成 SSE 推送载荷

### 3) Infra 层（基础设施）
- `ResumeParser`（Apache Tika）
  - PDF/Doc 文本解析
- `ResumeTextNormalizer`
  - 文本规范化（换行、噪声清洗）
- `ChunkerService`
  - 将全文切块并标注类型（CONTACT / EDUCATION / EXPERIENCE / SKILL / HONOR / LINK 等）
- `NameExtractor`
  - 从规范化文本中抽取姓名，规则优先
- `VectorStoreService`（规划）
  - 向量保存与检索（当前可通过开关禁用）

### 4) Data 层（实体与存储）
- 主要实体：
  - `Candidate`
  - `Contact`
  - `Education`
  - `Honor`
  - `Link`
  - `JobDescription`
  - `UploadBatch` / `UploadItem`
- 设计约束：
  - EXPERIENCE / SKILL 不落库，仅向量化
  - 候选人与 JD 为 `ManyToOne` 关系
  - 批量上传记录可追踪文件级状态

## 关键数据流

### A. 单文件解析
`HTTP -> ResumeController -> ResumeProcessService -> Parser -> Normalizer -> Chunker -> FactService -> DB + SSE`

### B. 批量上传（ZIP）
`HTTP -> UploadController -> 流式解压 -> storage/落盘 -> ResumeProcessService -> DB + Batch SSE`

### C. SSE 候选人事件
- 入库完成后推送 `CandidateStreamEvent`
- 字段包含 `candidateId / jdId / status / 联系方式 / 教育 / 荣誉 / 链接`

## 当前完成度
- **解析入库链路已完成**（RDBMS）
- **SSE 实时推送已完成**（候选人 & 批次）
- **JD 分桶已完成**（新增/编辑/删除 + 绑定）
- **向量检索为下一阶段任务**（Embedding 与向量库）

## 技术栈
- Java 17 / Spring Boot 3.5.x
- PostgreSQL（本地 Docker）
- Apache Tika（文档解析）
- SSE（实时推送）
- 本地 `storage/` 文件落盘

