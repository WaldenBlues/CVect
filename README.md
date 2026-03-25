# CVect

CVect 是一个面向招聘场景的简历处理与候选人管理系统。当前仓库包含 4 个主要部分：

- `backend/cvect`：Spring Boot 后端，负责 JD、上传批次、解析抽取、候选人查询、SSE 推送和向量检索
- `frontend`：Vue 3 单页控制台，负责 JD 管理、实时候选人列表、语义重排和状态流转
- `Qwen`：本地 FastAPI 模型服务，提供 embedding 和文本生成接口
- `infra/vllm`：可选的 OpenAI 兼容 vLLM 网关编排

项目当前仍然没有业务级鉴权或多租户能力；生产 compose 默认只补了前置 Basic Auth。README 以本地开发、联调和单机部署为主。

本次补充了一套单机容器化部署骨架，适合内网、测试环境或单台云主机验证；如果要对公网开放，至少还需要补鉴权、限流、TLS、备份和监控告警。

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
├── docker-compose.prod.yml     # 单机容器化部署编排
├── .env.prod.example           # 部署环境变量模板
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
- `GET /ready`
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

## 部署建议

### 先说结论

当前项目更适合先走“单机 Docker Compose 部署”，而不是一上来拆成多节点：

- 前端：构建成静态文件，由 nginx 托管，并反向代理 `/api`
- 后端：单个 Spring Boot 容器
- 数据库：单个 PostgreSQL 17 + `pgvector`
- 模型服务：单个 Qwen FastAPI 容器，默认 CPU 模式
- 持久化：Postgres 数据卷 + 后端上传目录数据卷 + Hugging Face 模型缓存卷

仓库已新增：

- `docker-compose.prod.yml`：生产编排入口
- `.env.demo.example`：面试 demo 推荐默认配置
- `.env.cn.example`：国内网络/云主机兼容配置示例
- `.env.prod.example`：部署变量示例
- `.env.cloud.example`：云主机部署变量模板
- `backend/cvect/Dockerfile`：后端镜像构建
- `frontend/Dockerfile`：前端镜像构建
- `frontend/nginx.conf`：前端静态托管与 `/api` 反向代理
- `scripts/bootstrap-ubuntu-docker.sh`：Ubuntu 云主机安装 Docker
- `scripts/cloud-deploy.sh`：云主机一键启动/停止/看日志
- `scripts/cloud-smoke-test.sh`：云主机最小可用性检查

### 为什么这样部署

- 前端源码默认请求 `/api/...`，生产环境最稳妥的方式是走同域名反向代理，避免单独处理 CORS
- SSE 依赖长连接，`frontend/nginx.conf` 已对流式接口关闭代理缓冲
- 简历上传会落本地文件，后端必须挂持久化卷，否则容器重建后会丢上传文件
- embedding 服务和数据库都不是无状态组件，至少要把模型缓存和数据库目录持久化

### 部署步骤

### 面试 demo 最短路径

如果你的目标是“单机/单台云主机，稳定展示一遍上传、解析、候选人列表和语义重排”，优先走这条：

```bash
cp .env.demo.example .env.demo
scripts/cloud-deploy.sh up
```

默认访问：

- 地址：`http://127.0.0.1:8088`
- Basic Auth 用户名：`demo`
- Basic Auth 密码：`demo123`

这套默认值针对 demo 做了收敛：

- 只走 CPU
- embedding batch 降到 `16`，降低小机器内存压力
- 入口自带 Basic Auth
- compose 会等到 `qwen` readiness 和 `/api/vector/health` 都通过再放前端起来

如果你部署在远程机器，只需要把 `127.0.0.1` 换成服务器 IP，并在 `.env.demo` 里改掉 Basic Auth 密码。

### 国内网络兼容路径

如果你的云主机对 Docker Hub 访问不稳定，优先走这条：

```bash
cp .env.cn.example .env.cn
scripts/cloud-deploy.sh up
```

这套配置做了三层兼容：

- 镜像层：`docker-compose.prod.yml` 现在支持通过环境变量覆盖基础镜像和 `postgres` 镜像
- 构建依赖层：支持 Maven / npm / pip / apt 使用国内镜像源
- 模型下载层：`qwen` 现在支持通过 `CVECT_HF_ENDPOINT` 切到可达的 Hugging Face 代理，或在缓存预热后用 `CVECT_HF_HUB_OFFLINE=true` 离线启动

推荐顺序：

1. 最稳妥：把需要的镜像同步到你自己的 ACR / TCR / SWR 私有仓库，然后在 `.env.cn` 里设置 `CVECT_*_IMAGE`
2. 快速尝试：对 Docker Hub 镜像使用 DaoCloud 加速前缀，仓库里的 `.env.cn.example` 已包含 `pgvector/pgvector` 和常见基础镜像
3. 仍然不稳：自建 Docker Registry pull-through cache，再把镜像地址改到你的内网仓库

如果你同时保留了多个环境文件，比如 `.env.demo` 和 `.env.cn`，请显式指定：

```bash
CVECT_ENV_FILE=.env.cn scripts/cloud-deploy.sh up
```

当前仓库已支持这些覆盖变量：

- `CVECT_POSTGRES_IMAGE`
- `CVECT_MAVEN_BUILD_IMAGE`
- `CVECT_JAVA_RUNTIME_IMAGE`
- `CVECT_NODE_BUILD_IMAGE`
- `CVECT_NGINX_RUNTIME_IMAGE`
- `CVECT_PYTHON_BASE_IMAGE`
- `CVECT_MAVEN_MIRROR_URL`
- `CVECT_NPM_REGISTRY`
- `CVECT_PIP_INDEX_URL`
- `CVECT_PIP_TRUSTED_HOST`
- `CVECT_DEBIAN_APT_MIRROR`
- `CVECT_UBUNTU_APT_MIRROR`
- `CVECT_ALPINE_APK_MIRROR`
- `CVECT_HF_ENDPOINT`
- `CVECT_HF_HUB_OFFLINE`

1. 复制环境变量模板并按实际环境调整：

```bash
cp .env.prod.example .env.prod
```

重点改这些值：

- `CVECT_BASIC_AUTH_USERNAME`
- `CVECT_BASIC_AUTH_PASSWORD`
- `CVECT_POSTGRES_PASSWORD`
- `CVECT_HTTP_PORT`
- `JAVA_OPTS`
- `CVECT_EMBEDDING_DEVICE`，默认 `cpu`；如果你后面切到 GPU/vLLM，再单独调整

2. 构建并启动：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

3. 查看状态：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f qwen
```

4. 访问入口：

- 前端：`http://<your-host>:${CVECT_HTTP_PORT}`
- API 通过前端 nginx 代理到后端，不需要再单独配置浏览器跨域

## 最小云部署

### 适用范围

这套方式针对“单台云服务器把前端、后端、Qwen、Postgres 全跑起来，并且能从公网访问”的最低成本实现。

适合：

- 演示
- 毕设/课程项目
- 小范围内测

不适合：

- 直接公网开放给未知用户
- 高并发
- 多副本和高可用

### 云上拓扑

一台 Ubuntu 云主机即可：

- `frontend` 对外暴露 `80`
- `backend` 只在 Docker 内网暴露 `8080`
- `qwen` 只在 Docker 内网暴露 `8001`
- `postgres` 只在 Docker 内网暴露 `5432`

这样公网只看到前端入口，不直接暴露数据库和模型服务。

### 云主机实际步骤

1. 开一台 Ubuntu 云主机，把安全组或防火墙至少放行：

- `22/tcp`：SSH
- `80/tcp`：网页访问

不要对公网放行：

- `5432`
- `8001`
- `8080`

2. 把仓库传到云主机，例如：

```bash
git clone <your-repo-url>
cd CVect
```

3. 安装 Docker：

```bash
sudo bash scripts/bootstrap-ubuntu-docker.sh
```

如果你的云主机到 Docker 官方 apt 源不稳定，可以先切国内镜像再执行：

```bash
export CVECT_BOOTSTRAP_UBUNTU_APT_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu
export CVECT_BOOTSTRAP_DOCKER_APT_REPO=https://mirrors.aliyun.com/docker-ce/linux/ubuntu
export CVECT_BOOTSTRAP_DOCKER_GPG_URL=https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg
sudo -E bash scripts/bootstrap-ubuntu-docker.sh
```

如果你刚被加入 `docker` 组，重新登录一次。

4. 准备云部署环境变量：

```bash
cp .env.cloud.example .env.cloud
```

至少修改：

- `CVECT_POSTGRES_PASSWORD`
- `JAVA_OPTS`
- `CVECT_BASIC_AUTH_PASSWORD`

如果你的云主机比较小，先保持：

- `CVECT_EMBEDDING_DEVICE=cpu`
- `CVECT_EMBEDDING_BATCH_SIZE=32`

5. 启动整套服务：

```bash
scripts/cloud-deploy.sh up
```

6. 看运行状态：

```bash
scripts/cloud-deploy.sh status
scripts/cloud-deploy.sh logs backend
scripts/cloud-deploy.sh logs qwen
```

7. 做最小检查：

```bash
export CVECT_BASIC_AUTH_USERNAME=demo
export CVECT_BASIC_AUTH_PASSWORD=demo123
scripts/cloud-smoke-test.sh http://127.0.0.1
scripts/cloud-smoke-test.sh http://<your-server-ip>
```

### 你最终会得到什么

- 浏览器访问 `http://<你的云服务器IP>/`
- 前端可打开
- 前端通过 `/api` 访问后端
- 后端通过内网访问 `qwen` 和 `postgres`

### 这套云部署的现实限制

- 首次启动 `qwen` 会下载模型，可能要很久
- 云主机内存太小会导致 Python 模型服务或 Java 进程起不来
- 现在是 HTTP，不是 HTTPS
- 生产 compose 默认只带前置 Basic Auth，还没有业务级登录态/RBAC

### 部署限制

- 当前生产 compose 默认启用前置 Basic Auth；但后端还没有内建业务级鉴权/RBAC
- 模型首次启动需要下载 Hugging Face 权重，时间和磁盘占用都要预留
- 默认是单实例部署；SSE、上传队列和本地存储都还没按多副本场景设计
- `docker-compose.prod.yml` 默认用 CPU 跑 Qwen，只适合功能验证或小规模使用

### 如果后面要上正式环境

建议优先补这几项：

- 接入登录鉴权和最基础的 RBAC
- 给 nginx 做 HTTPS 终止
- Postgres 定时备份
- 统一日志、指标和告警
- 把模型服务切到 GPU 或 `infra/vllm` 路线
- 明确上传文件保留策略和磁盘清理策略

## 健康检查

```bash
curl -s http://localhost:8080/api/resumes/health
curl -s http://localhost:8080/api/vector/health
curl -s http://localhost:8001/ready
curl -u demo:demo123 -s http://localhost:8088/healthz
```

## 搜索指标与缓存观测

Spring Boot Actuator 已暴露搜索耗时和缓存指标，可直接通过 `/actuator/metrics/{name}` 查看。

常用指标：

- `cvect.search.request`：`/api/search` 端到端请求耗时（包含缓存命中）
- `cvect.search.compute`：实际语义计算耗时（仅 cache miss 会进入）
- `cvect.cache.hit.rate`：当前缓存命中率，按 `cache` tag 区分
- `cache.gets` / `cache.puts`：Caffeine 标准缓存读写统计

常用查询示例：

```bash
curl -s http://localhost:8080/actuator/metrics/cvect.search.request
curl -s http://localhost:8080/actuator/metrics/cvect.search.compute
curl -s http://localhost:8080/actuator/metrics/cvect.cache.hit.rate
curl -s "http://localhost:8080/actuator/metrics/cvect.cache.hit.rate?tag=cache:searchQueryEmbeddings"
curl -s "http://localhost:8080/actuator/metrics/cvect.cache.hit.rate?tag=cache:semanticSearchResponses"
```

判读建议：

- `cvect.search.request` 明显低于 `cvect.search.compute` 的 p95/p99，通常表示缓存有效
- `searchQueryEmbeddings` 命中率低，说明 JD 文本复用少或 key 归一化不足
- `semanticSearchResponses` 命中率长期接近 `0`，说明搜索条件变化太频繁，结果缓存收益有限

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
