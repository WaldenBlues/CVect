# CVect 代码设计可读性报告（不含 test）

本文只基于当前 `backend/cvect/src/main/java` 的代码结构进行设计解读，聚焦类的职责与设计方式，不讨论功能完成度或效果。

**整体结构概览**
- 入口层：`com.walden.cvect`
- Web 层：`com.walden.cvect.web.controller`
- Service 层：`com.walden.cvect.service`
- Infra 层：`com.walden.cvect.infra.{parser,process,embedding,vector}`
- Model 层：`com.walden.cvect.model`、`com.walden.cvect.model.entity`、`com.walden.cvect.model.fact`
- Repository 层：`com.walden.cvect.repository`
- Exception 层：`com.walden.cvect.exception`

该结构基本遵循典型的 Spring Boot 分层风格，职责分离清晰，类体量普遍较小，便于阅读与定位。

---

**入口层设计**

`CvectApplication`（`backend/cvect/src/main/java/com/walden/cvect/CvectApplication.java`）
- 仅作为应用启动入口，使用 `@SpringBootApplication` 与 `@EnableJpaRepositories`，没有引入额外逻辑。
- 该类保持最小职责，符合“启动类只负责启动”的惯例。

---

**Web Controller 设计**

`ResumeController`（`backend/cvect/src/main/java/com/walden/cvect/web/controller/ResumeController.java`）
- 控制器仅聚合请求参数与调用 service，不含业务判断，保持了较清晰的职责边界。
- API 层返回结构采用 `Map`，数据结构简单直接，降低 DTO 类数量，但可读性依赖 service 返回值说明。

`SearchController`（`backend/cvect/src/main/java/com/walden/cvect/web/controller/SearchController.java`）
- 使用内部 `record` 定义请求与响应，结构紧凑，靠近调用点，便于读者理解数据结构。
- 控制器内的聚合逻辑被拆为 `resolveChunkTypes`、`aggregateAndSort`、`clampTopK`，提高主流程可读性。

---

**Service 层设计**

`ResumeProcessService`（`backend/cvect/src/main/java/com/walden/cvect/service/ResumeProcessService.java`）
- 负责 orchestrate 解析 -> 规范化 -> 分块 -> 事实处理的流程。
- 通过 `parseAndNormalize` 与 `processChunks` 两个私有方法拆解流程，降低方法复杂度。
- 返回 `ProcessResult` 记录式结构，减少额外 DTO 类。

`ResumeFactService`（`backend/cvect/src/main/java/com/walden/cvect/service/ResumeFactService.java`）
- 主要角色为“事实抽取结果 -> 持久化”调度器。
- `handleExtractedData` 将类型分发逻辑集中，避免 switch 分散在循环内，读起来更加稳定。

`ChunkerService`（`backend/cvect/src/main/java/com/walden/cvect/service/ChunkerService.java`）
- 纯接口定义，强调可替换性。
- 在当前结构中作为分块策略抽象点。

`DefaultChunkerService`（`backend/cvect/src/main/java/com/walden/cvect/service/DefaultChunkerService.java`）
- 采用规则驱动的状态机式分块。
- 使用常量与关键词列表聚合规则参数，减少硬编码散落。
- 将“文本判断”拆成 `containsAny` / `endsWithAny`，增强表达力与可维护性。
- `inferType` 逻辑集中，读者可从上到下理解类型推断路径。

---

**Infra 层设计**

解析器

`ResumeParser`（`backend/cvect/src/main/java/com/walden/cvect/infra/parser/ResumeParser.java`）
- 只暴露 `parse(InputStream, String)`，隐藏具体实现细节。

`TikaResumeParser`（`backend/cvect/src/main/java/com/walden/cvect/infra/parser/TikaResumeParser.java`）
- 内部持有 `AutoDetectParser`，通过 `ParseContext` 进行解析配置。
- 将“解析时策略”集中在 `createParseContext`，便于阅读与隔离配置点。
- 解析错误集中转换为 `ResumeParseException`，保持调用者语义一致。

规范化

`ResumeTextNormalizer`（`backend/cvect/src/main/java/com/walden/cvect/infra/process/ResumeTextNormalizer.java`）
- 单方法接口，用于清晰表达预处理责任。

`DefaultResumeTextNormalizer`（`backend/cvect/src/main/java/com/walden/cvect/infra/process/DefaultResumeTextNormalizer.java`）
- 采用顺序化处理逻辑（换行统一、空行压缩、链接过滤、页码删除），规则按文本变换步骤排列，阅读成本低。

Embedding

`EmbeddingConfig`（`backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingConfig.java`）
- 通过 `@ConfigurationProperties` 进行配置聚合，类结构明确，对外只暴露 getter/setter。

`EmbeddingService`（`backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java`）
- 通过 WebClient 调用外部服务，明确区分 `embed` 与 `embedBatch`。
- 使用内部 `record` 表示 request/response，降低额外 DTO 类数量。
- 采用“返回空向量或抛错”的双策略，阅读上具备明确 fallback 路径。

Vector

`VectorStoreConfig`（`backend/cvect/src/main/java/com/walden/cvect/infra/vector/VectorStoreConfig.java`）
- 配置集中，便于识别 pgvector 相关参数。

`VectorStoreService`（`backend/cvect/src/main/java/com/walden/cvect/infra/vector/VectorStoreService.java`）
- 分层明确：保存、搜索、删除、创建索引四块职责。
- SQL 构建采用白名单枚举验证，清楚表达“安全拼接”的设计意图。
- `SearchResult` 用 record 承载，不引入额外 DTO，表达直观。

---

**Model 层设计**

基础模型

`ParseResult`（`backend/cvect/src/main/java/com/walden/cvect/model/ParseResult.java`）
- 不可变类，字段明确，getter 风格一致。
- 语义聚合清晰，读者能直接了解解析阶段输出内容。

`ResumeChunk`（`backend/cvect/src/main/java/com/walden/cvect/model/ResumeChunk.java`）
- 不可变结构，长度在构造时计算，减少外部重复计算。

`ChunkType`（`backend/cvect/src/main/java/com/walden/cvect/model/ChunkType.java`）
- 明确表达分块类型域，集中枚举。

实体模型

`Contact` / `Education` / `Experience` / `Honor` / `Link`
- 统一风格：UUID 主键，`candidateId` 作为外部关联，`createdAt` 使用 `@PrePersist` 自动写入。
- 构造器选择最少字段集合，保持实体实例化简单。
- `equals/hashCode` 统一基于 `id`，风格一致。

`ResumeChunkVector`（`backend/cvect/src/main/java/com/walden/cvect/model/entity/vector/ResumeChunkVector.java`）
- 用 JPA 表达向量数据实体，保留 `chunkType`、`content`、`embedding`。
- 明确注释区分 H2 与生产向量存储策略，降低读者误解。

---

**Fact 子系统设计**

`ChunkFactContext`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/ChunkFactContext.java`）
- 封装事实判断上下文，将 `LazyFeatures` 作为成员，避免多次扫描文本。

`LazyFeatures`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/LazyFeatures.java`）
- 懒加载特征缓存，使用 `Boolean` 包装延迟求值。
- 统一在单一类中管理“文本特征判断”，减少散乱逻辑。

`Regex`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/Regex.java`）
- 集中管理正则，减少重复定义。

`FactDecision`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/FactDecision.java`）
- 采用静态工厂方法 `accept/reject/abstain` 表达语义，避免 new 的可读性损耗。

`ChunkFactRule`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/ChunkFactRule.java`）
- 函数式接口，允许规则以 lambda 方式表达。
- 提供 `ALWAYS_TRUE/ALWAYS_FALSE` 作为默认常量，便于测试或扩展。

`FactChunkSelector`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/FactChunkSelector.java`）
- 简洁接口，强调“是否接受”为唯一职责。

`RuleBasedFactChunkSelector`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/RuleBasedFactChunkSelector.java`）
- 将 `ChunkFactRule` 列表按顺序执行并支持 `ABSTAIN` 的决策链式模型。
- 通过 `buildContext` 统一构造上下文对象，阅读一致性高。

`DefaultFactRules`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/DefaultFactRules.java`）
- 集中注册默认规则列表，便于替换或扩展。

规则类

`HeaderRule` / `ContactRule` / `LinkRule` / `ExperienceRule` / `SkillRule` / `HonorRule`
- 每个规则为单一职责类，代码体量短。
- 规则内部基于 `ChunkFactContext` 的特征判断，语义清晰。

抽取器

`FactExtractor`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/extract/FactExtractor.java`）
- 抽取与支持判断分离，配合 `ExtractorMode` 表达提取策略。

`ExtractorMode`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/extract/ExtractorMode.java`）
- 明确区分 “只允许一个” 与 “可叠加”。

`FactExtractorDispatcher`（`backend/cvect/src/main/java/com/walden/cvect/model/fact/extract/FactExtractorDispatcher.java`）
- 以 mode 作为执行顺序控制，先 ADDITIVE 再 EXCLUSIVE。
- 该设计让扩展的抽取器具备统一的调度语义。

`ContactExtractor` / `EducationExtractor` / `HonorExtractor` / `LinkExtractor`
- 每个 extractor 只处理一个类型，且包含局部清洗逻辑。
- 类短小且直观，易于新增其他类型的抽取器。

---

**Repository 层设计**

`ContactJpaRepository` / `EducationJpaRepository` / `ExperienceJpaRepository` / `HonorJpaRepository` / `LinkJpaRepository`
- 统一继承 `JpaRepository`，并提供 `findByCandidateId` 作为常见查询入口。
- 设计简洁，几乎不带自定义行为。

`FactRepository`（`backend/cvect/src/main/java/com/walden/cvect/repository/FactRepository.java`）
- 对多个 JPA 仓储的门面封装，减少 service 层对多个 repository 的依赖。
- 提供“按实体类型的保存方法”，读者易于从名称推断行为。

---

**异常设计**

`ResumeParseException` / `ResumeProcessingException`
- 语义化异常区分解析层与处理层。
- 保留原始异常作为 cause，利于排错。

---

**可读性角度的整体评价**
- 类职责清晰，普遍“短小而单一”。
- 抽象层次一致，接口命名清楚。
- 规则体系与抽取器体系分离，避免规则判断与抽取逻辑耦合。
- 入口控制器与 service 层协作路径直观。

如需补充报告（比如希望按包生成类关系图、或加入流程图文字版本），告诉我你期望的输出格式。 
