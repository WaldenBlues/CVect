# CVect

CVect 是一个面向 HR 场景的简历处理系统：支持 JD 管理、批量上传、简历解析、结构化入库、实时状态推送（SSE）与候选人招聘状态流转。

## 当前项目状态

### 已实现（可用）
- JD 管理：新增 / 列表 / 详情 / 编辑 / 删除（含关联校验）
- 简历处理主链路：上传 -> 解析（Tika）-> 分块 -> 事实抽取 -> 入库
- 批量上传：普通多文件 + ZIP 上传
- 批次运营接口：
  - `GET /api/uploads/batches/{id}`
  - `GET /api/uploads/batches/{id}/items`
  - `POST /api/uploads/batches/{id}/retry-failed`
- 重试幂等体验：`retry-failed` 重复调用不会重复入队同一失败项
- 候选人列表与状态更新：支持 `TO_CONTACT / TO_INTERVIEW / REJECTED`
- 快照 + SSE：
  - 候选人增量推送（`/api/candidates/stream`）
  - 批次进度推送（`/api/uploads/batches/{id}/stream`）
- 文件幂等：基于 `file_hash` 去重，避免重复解析/重复入库

### 当前默认环境
- 运行时默认数据库：PostgreSQL（`backend/cvect/src/main/resources/application.yml`）
- 测试数据库：H2（`backend/cvect/src/test/resources/application.properties`）
- 即：开发/运行不依赖 H2，测试可直接用 H2 跑通

## 技术栈

- Backend: Java 17, Spring Boot 3.5, Spring Data JPA, Hibernate
- Parser: Apache Tika
- Realtime: SSE
- DB: PostgreSQL（运行）/ H2（测试）
- Frontend: Vue 3 + Vite
- Embedding 调用：WebClient（对接本地 Python 服务，当前为框架可配置）

## 快速启动

### 1) 启动 PostgreSQL
```bash
docker compose up -d postgres
```

### 2) 启动后端
```bash
cd backend/cvect
./mvnw -Dmaven.test.skip=true spring-boot:run
```

### 3) 启动前端
```bash
cd frontend
npm install
npm run dev -- --host
```

### 4) 健康检查
```bash
curl http://localhost:8080/api/resumes/health
```

## 测试与质量门禁

```bash
cd backend/cvect
./mvnw -q -DskipTests compile
./mvnw -q test
```

## 关键接口（当前）

- JD：
  - `GET /api/jds`
  - `POST /api/jds`
  - `GET /api/jds/{id}`
  - `PUT /api/jds/{id}`
  - `DELETE /api/jds/{id}`
- 简历：
  - `POST /api/resumes/parse`（需要 `jdId`）
- 上传与批次：
  - `POST /api/uploads/resumes`
  - `POST /api/uploads/zip`
  - `GET /api/uploads/batches/{id}`
  - `GET /api/uploads/batches/{id}/items`
  - `POST /api/uploads/batches/{id}/retry-failed`
- 候选人：
  - `GET /api/candidates?jdId=...`
  - `PATCH /api/candidates/{id}/recruitment-status`
- SSE：
  - `GET /api/candidates/stream`
  - `GET /api/uploads/batches/{id}/stream`

## 下一阶段计划（已对齐当前方向）

### A. 本地模型服务接入（Qwen）
- 接入本地 Python 微服务，提供：
  - 简历/JD 向量化（Embedding）
  - 关键词抽取与结构化输出
- Java 侧通过可配置 HTTP 客户端调用，保持模型与主业务解耦

### B. 语义检索与匹配闭环
- 将简历关键内容向量化写入向量库（pgvector 或独立向量库）
- 新增 JD -> 候选人语义召回与排序接口
- 输出可解释结果（命中关键词、匹配分数、来源片段）

### C. 可靠性与可观测性
- 强化任务队列可观测性（重试次数、死信/终态统计）
- 降低噪声日志（特别是 worker 轮询 SQL）
- 增加关键链路指标与告警（解析失败率、重试成功率、队列积压）

## 项目结构

```text
CVect/
├── backend/cvect/        # Spring Boot 后端
├── frontend/             # Vue 前端
├── Qwen/                 # 本地模型服务实验目录（Python）
├── docker-compose.yml    # PostgreSQL（pgvector 镜像）
├── ARCHITECTURE_OVERVIEW.md
└── CODE_DESIGN_REPORT.md
```

---

如你准备继续推进 Qwen 本地化能力，可以直接在此 README 基础上追加「服务契约（request/response）」和「联调步骤」章节，避免后续多人协作出现接口漂移。
