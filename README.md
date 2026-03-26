# CVect

![Java 17](https://img.shields.io/badge/Java-17-2f5d95?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f?style=flat-square)
![Vue 3](https://img.shields.io/badge/Vue-3-42b883?style=flat-square)
![FastAPI](https://img.shields.io/badge/FastAPI-Embedding%20Service-009688?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-336791?style=flat-square)
![Docker Compose](https://img.shields.io/badge/Docker%20Compose-Single%20Stack-2496ed?style=flat-square)
![CPU Only](https://img.shields.io/badge/Qwen-CPU%20Only%20Embedding-8b5cf6?style=flat-square)

CVect 是一个面向招聘场景的简历处理与候选人管理系统，用一套单仓库把 `JD 管理`、`简历上传解析`、`候选人看板`、`SSE 实时增量流` 和 `pgvector 语义检索` 串起来。

当前仓库已经收敛成单机友好的默认形态：

- 单一编排入口：`docker-compose.yml`
- 单一配置源：根目录 `.env`
- 单一模型路径：本地 `Qwen` CPU embedding 服务
- 双运行脚本：`scripts/local-run.sh` 和 `scripts/server-run.sh`

> 当前目标是本地开发、联调、演示和单台云主机部署，不是完整的生产级多租户系统。
> 仓库默认只提供前置 Basic Auth，没有业务级登录、RBAC、限流、TLS 和备份策略。

## 为什么这个仓库值得看

- 招聘业务闭环完整：从 JD 到候选人列表、语义检索、状态流转都能直接跑通
- 离线部署友好：支持本地预下载 Hugging Face cache，再上传服务器离线启动
- 小机器可运行：默认就是 CPU-only embedding，配置收敛到 2C4G 也能验证链路
- 工程入口简单：本地、服务器、离线 cache、探活脚本都在 `scripts/` 下
- 前后端边界清晰：前端单页看板，后端 Spring Boot，模型服务 FastAPI，数据库 PostgreSQL + `pgvector`

## 快速了解

| 场景 | 入口命令 | 默认访问 |
| --- | --- | --- |
| 本地联调 | `scripts/local-run.sh start` | 前端 `http://127.0.0.1:5173` |
| 单机部署 | `scripts/server-run.sh up` | 前端 `http://127.0.0.1:8088` |
| 服务器不构建 | `scripts/server-run.sh up-no-build` | 适合本地 `build/save` 后服务器 `load` |
| 离线模型缓存 | `scripts/qwen-offline-cache.sh prefetch` | 生成 HF cache 供服务器解包 |

## 当前实现

- JD 管理：新增、列表、详情、编辑、删除
- 简历上传：多文件上传、ZIP 上传、失败项重试、幂等去重
- 简历处理：Apache Tika 解析、事实抽取、分块、异步向量入库
- 候选人视图：按 JD 查询、实时增量流、招聘状态更新
- 语义检索：按 JD 文本进行向量召回，支持 Experience / Skill 加权
- 运行支撑：Flyway 迁移、Actuator、pgvector、H2 测试配置

前端现在是一个面向招聘运营的单页看板，不是通用 UI 框架。

## 导航

- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [快速启动](#快速启动)
- [部署建议](#部署建议)
- [最小云部署](#最小云部署)
- [健康检查](#健康检查)
- [关键配置](#关键配置)
- [测试](#测试)
- [Hugging Face 故障排查](#hugging-face-故障排查)

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
├── Qwen/                       # FastAPI embedding 服务与离线模型脚本
├── scripts/                    # 启动、部署、离线 cache、探活脚本
├── docker-compose.yml          # 唯一容器编排入口
├── .env                        # 唯一环境变量文件
├── scripts/local-run.sh        # 本地一键启动脚本
└── scripts/server-run.sh       # 服务器一键部署脚本
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
- Node.js 20+
- Python 3.10+
- Docker + Docker Compose

首次启动前建议确认：

- 机器可以拉取 Hugging Face 模型，或者本地已有缓存
- `5432`、`5173`、`8001`、`8080` 端口未被占用

## 首次准备

### 1. Python 模型服务依赖

```bash
cd Qwen
python3 -m venv .venv
source .venv/bin/activate
pip install --extra-index-url https://download.pytorch.org/whl/cpu torch==2.10.0+cpu
pip install -r requirements-service.txt
```

### 2. 前端依赖

```bash
cd frontend
npm install
```

仓库内的 [frontend/.npmrc](/home/walden/Walden_project/CVect/frontend/.npmrc) 默认指向 `npmmirror`，并启用 `replace-registry-host=always`，用于减少锁文件和镜像源漂移。

后端使用 Maven Wrapper，不需要单独安装 Maven。

## 快速启动

### 推荐方式：一键启动

在仓库根目录执行：

```bash
scripts/local-run.sh start
```

该脚本会尝试启动：

- PostgreSQL：`localhost:5432`
- Qwen FastAPI embedding 服务：`localhost:8001`
- Spring Boot 后端：`localhost:8080`
- Vite 前端：`localhost:5173`

常用命令：

```bash
scripts/local-run.sh status
scripts/local-run.sh stop
scripts/local-run.sh restart
```

说明：

- `scripts/local-run.sh` 会在 `frontend/node_modules` 缺失时自动执行 `npm install`
- `scripts/local-run.sh` 不会自动安装 Python 依赖，`Qwen` 环境需要先准备好
- `scripts/local-run.sh` 会优先读取根目录 `.env`
- 本地 PostgreSQL 默认也走 `CVECT_POSTGRES_IMAGE`，未覆盖时使用 DaoCloud 的 `pgvector` 镜像
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

- `docker-compose.yml`：唯一容器编排入口
- `.env`：唯一环境变量文件
- `backend/cvect/Dockerfile`：后端镜像构建
- `frontend/Dockerfile`：前端镜像构建
- `frontend/nginx.conf`：前端静态托管与 `/api` 反向代理
- `scripts/bootstrap-ubuntu-docker.sh`：Ubuntu 云主机安装 Docker
- `scripts/server-run.sh`：云主机一键启动/停止/看日志
- `scripts/cloud-smoke-test.sh`：云主机最小可用性检查

### 为什么这样部署

- 前端源码默认请求 `/api/...`，生产环境最稳妥的方式是走同域名反向代理，避免单独处理 CORS
- SSE 依赖长连接，`frontend/nginx.conf` 已对流式接口关闭代理缓冲
- 简历上传会落本地文件，后端必须挂持久化卷，否则容器重建后会丢上传文件
- embedding 服务和数据库都不是无状态组件，至少要把模型缓存和数据库目录持久化

### 默认部署路径

如果你的目标是“单机/单台云主机，稳定展示上传、解析、候选人列表和语义重排”，只保留这一套配置：

```bash
scripts/qwen-offline-cache.sh prefetch
scripts/server-run.sh up
```

默认访问：

- 地址：`http://127.0.0.1:8088`
- Basic Auth 用户名：`demo`
- Basic Auth 密码：`demo123`

后续所有部署和探活脚本都默认读取仓库根目录的 `.env`。
`docker-compose.yml` 是唯一编排入口，部署参数以 `.env` 为唯一真源。

这套默认值同时收敛了 demo 展示和国内网络兼容：

- 只走 CPU
- 默认是 embedding-only，`qwen` 不再加载 generation 模型
- embedding batch 默认降到 `1`，并限制输入长度与 chunk 长度，降低小机器内存压力
- 入口自带 Basic Auth
- 后端健康检查只看 Web/数据库存活，不再因为向量服务未就绪阻塞前端
- `pgvector` 和常见基础镜像默认走 DaoCloud 前缀
- Maven / npm / pip / apt 默认走国内镜像源
- `qwen` 默认走离线 Hugging Face cache，不再要求服务器在线下载模型

如果你部署在远程机器，只需要把 `127.0.0.1:8088` 换成服务器 IP 和实际端口，并在 `.env` 里改掉 Basic Auth 密码。

如果把 `CVECT_EMBEDDING_SERVICE_URL` 改成外部 OpenAI-compatible embedding 服务，
`scripts/server-run.sh up` 会自动跳过本地 `qwen` 容器，只拉起 `postgres/backend/frontend`。

如果你准备在本地构建镜像再上传到服务器，不要在服务器执行会触发重新构建的
`scripts/server-run.sh up`，改用：

```bash
scripts/server-run.sh up-no-build
```

对应重建容器但不在服务器重新 build 的命令是：

```bash
scripts/server-run.sh restart-no-build
```

推荐顺序：

1. 最稳妥：把需要的镜像同步到你自己的 ACR / TCR / SWR 私有仓库，然后在 `.env` 里设置 `CVECT_*_IMAGE`
2. 快速尝试：直接使用仓库默认的 DaoCloud 前缀
3. 仍然不稳：自建 Docker Registry pull-through cache，再把镜像地址改到你的内网仓库

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
- `CVECT_HF_LOCAL_FILES_ONLY`
- `CVECT_HF_HUB_DISABLE_XET`
- `CVECT_HTTP_PROXY`
- `CVECT_HTTPS_PROXY`
- `CVECT_NO_PROXY`

1. 直接修改仓库根目录 `.env` 并按实际环境调整：

重点改这些值：

- `CVECT_BASIC_AUTH_USERNAME`
- `CVECT_BASIC_AUTH_PASSWORD`
- `CVECT_POSTGRES_PASSWORD`
- `CVECT_HTTP_PORT`
- `JAVA_OPTS`
- `CVECT_EMBEDDING_DEVICE`，默认 `cpu`；如果你后面切到 GPU 或外部 embedding 服务，再单独调整
- `CVECT_HF_CACHE_DIR`，默认 `./.runtime/huggingface`

2. 在一台能访问 Hugging Face 的机器上预下载 Qwen 模型：

```bash
scripts/qwen-offline-cache.sh prefetch
scripts/qwen-offline-cache.sh verify
```

如果是远程服务器部署，再打包并传过去：

```bash
scripts/qwen-offline-cache.sh pack
scp .artifacts/qwen-hf-cache.tgz <server>:/tmp/
```

服务器上解包：

```bash
scripts/qwen-offline-cache.sh unpack /tmp/qwen-hf-cache.tgz
scripts/qwen-offline-cache.sh verify
```

3. 构建并启动：

```bash
scripts/server-run.sh up
```

4. 查看状态：

```bash
scripts/server-run.sh status
scripts/server-run.sh logs backend
scripts/server-run.sh logs qwen
```

5. 访问入口：

- 前端：`http://<your-host>:<CVECT_HTTP_PORT>`
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

- `frontend` 对外暴露 `CVECT_HTTP_PORT`，默认 `8088`
- `backend` 只在 Docker 内网暴露 `8080`
- `qwen` 只在 Docker 内网暴露 `8001`
- `postgres` 只在 Docker 内网暴露 `5432`

这样公网只看到前端入口，不直接暴露数据库和模型服务。

### 云主机实际步骤

1. 开一台 Ubuntu 云主机，把安全组或防火墙至少放行：

- `22/tcp`：SSH
- `8088/tcp`：网页访问；如果你修改了 `.env` 里的 `CVECT_HTTP_PORT`，这里放行对应端口

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

脚本会优先读取根目录 `.env` 里的 `CVECT_BOOTSTRAP_*`。当前仓库默认已经填好了国内镜像地址。

如果你刚被加入 `docker` 组，重新登录一次。

4. 准备云部署环境变量：直接修改根目录 `.env`

至少修改：

- `CVECT_POSTGRES_PASSWORD`
- `JAVA_OPTS`
- `CVECT_BASIC_AUTH_PASSWORD`

如果你的云主机比较小，先保持：

- `CVECT_EMBEDDING_DEVICE=cpu`
- `CVECT_EMBEDDING_BATCH_SIZE=1`
- `CVECT_EMBEDDING_MAX_INPUT_LENGTH=1024`
- `CVECT_CHUNK_MAX_LENGTH=1000`
- `CVECT_VECTOR_MAX_CONCURRENT_WRITES=1`
- `CVECT_VECTOR_INGEST_WORKER_CLAIM_BATCH_SIZE=1`
- `JAVA_OPTS=-Xms384m -Xmx1024m`

5. 启动整套服务：

```bash
scripts/qwen-offline-cache.sh unpack /tmp/qwen-hf-cache.tgz
scripts/server-run.sh up
```

6. 看运行状态：

```bash
scripts/server-run.sh status
scripts/server-run.sh logs backend
scripts/server-run.sh logs qwen
```

7. 做最小检查：

```bash
scripts/cloud-smoke-test.sh
scripts/cloud-smoke-test.sh http://<your-server-ip>:8088
```

### 你最终会得到什么

- 浏览器访问 `http://<你的云服务器IP>:8088/`
- 前端可打开
- 前端通过 `/api` 访问后端
- 后端通过内网或外部地址访问 embedding 服务和 `postgres`

### 这套云部署的现实限制

- 如果没有提前准备离线 Hugging Face cache，`qwen` 首次启动会卡在模型下载
- 云主机内存太小会导致 Python 模型服务或 Java 进程起不来
- 现在是 HTTP，不是 HTTPS
- 默认部署只带前置 Basic Auth，还没有业务级登录态/RBAC

### 部署限制

- 当前默认部署启用前置 Basic Auth；但后端还没有内建业务级鉴权/RBAC
- 如果不走离线 cache，模型首次启动会触发 Hugging Face 权重下载，时间和磁盘占用都要预留
- 默认是单实例部署；SSE、上传队列和本地存储都还没按多副本场景设计
- `docker-compose.yml` 默认用 CPU 跑 embedding-only Qwen，只适合功能验证或小规模使用

### 如果后面要上正式环境

建议优先补这几项：

- 接入登录鉴权和最基础的 RBAC
- 给 nginx 做 HTTPS 终止
- Postgres 定时备份
- 统一日志、指标和告警
- 把模型服务切到更高规格机器，或拆到独立的外部 embedding 服务
- 明确上传文件保留策略和磁盘清理策略

## 健康检查

```bash
curl -s http://localhost:8080/api/resumes/health
curl -s http://localhost:8080/api/vector/health
# 只有在使用本地 qwen 时才需要：
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
- `CVECT_EMBEDDING_API_FORMAT`，支持 `auto` / `native` / `openai` / `llama_cpp`
- `CVECT_EMBEDDING_HEALTH_URL`，默认 `http://localhost:8001/ready`
- `CVECT_EMBEDDING_MODEL`
- `CVECT_EMBEDDING_TIMEOUT_SECONDS`
- `CVECT_EMBEDDING_BATCH_SIZE`
- `CVECT_EMBEDDING_DIMENSION`，默认 `1024`
- `CVECT_EMBEDDING_MAX_INPUT_LENGTH`
- `CVECT_CHUNK_MAX_LENGTH`，默认 `1000`
- `CVECT_CHUNK_OVERLAP`，默认 `100`
- `CVECT_TORCH_NUM_THREADS`
- `CVECT_TORCH_NUM_INTEROP_THREADS`
- `CVECT_EMBEDDING_MAX_CONCURRENT_REQUESTS`
- `CVECT_MALLOC_ARENA_MAX`

### Vector

- `CVECT_VECTOR_ENABLED`，默认 `true`
- `CVECT_VECTOR_INGEST_WORKER_ENABLED`，默认 `true`
- `CVECT_VECTOR_TABLE`，默认 `resume_chunks`
- `CVECT_VECTOR_INDEX_TYPE`，默认 `hnsw`
- `CVECT_VECTOR_METRIC`，默认 `cosine`
- `CVECT_VECTOR_DIMENSION`，默认继承 embedding 维度
- `CVECT_VECTOR_MAX_CONCURRENT_WRITES`
- `CVECT_VECTOR_INGEST_WORKER_CLAIM_BATCH_SIZE`
- `CVECT_VECTOR_INGEST_WORKER_MAX_POOL`

### Upload

- `CVECT_UPLOAD_STORAGE_DIR`，默认 `storage`
- `CVECT_UPLOAD_MAX_INFLIGHT_ITEMS`，默认 `2000`
- `CVECT_UPLOAD_MAX_FILES_PER_ZIP`，默认 `2000`

需要注意：

- `CVECT_EMBEDDING_DIMENSION` 与 `CVECT_VECTOR_DIMENSION` 必须一致
- 切换 embedding 模型前，需要确认向量表维度和现有索引是否仍然兼容
- 2C4G 机器建议把 `CVECT_EMBEDDING_BATCH_SIZE=1`、`CVECT_EMBEDDING_MAX_INPUT_LENGTH=1024`
  、`CVECT_CHUNK_MAX_LENGTH=1000`、`CVECT_VECTOR_MAX_CONCURRENT_WRITES=1`
  和 `JAVA_OPTS=-Xms384m -Xmx1024m`
- 如果只需要先拉起 Web 页面、暂时不启动 `qwen`，可在 `.env` 中设置
  `CVECT_VECTOR_ENABLED=false` 和 `CVECT_VECTOR_INGEST_WORKER_ENABLED=false`
- 如果要改用外部 CPU embedding 服务，可把 `CVECT_EMBEDDING_SERVICE_URL` 指到
  `/v1/embeddings`，并设置 `CVECT_EMBEDDING_API_FORMAT=openai`
- 如果要改用 `llama.cpp` / GGUF 服务，可把 `CVECT_EMBEDDING_SERVICE_URL` 指到
  `/embedding`，并设置 `CVECT_EMBEDDING_API_FORMAT=llama_cpp`
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

例如：

```bash
cd backend/cvect
./mvnw -DexcludedGroups= -Dgroups=postgres -Dtest=com.walden.cvect.infra.vector.VectorStoreServicePostgresTest test
```

### 前端

```bash
cd frontend
npm test
```

当前前端测试主要覆盖语义匹配工具函数：`src/utils/semanticMatching.test.mjs`。

## Hugging Face 故障排查

如果 `qwen` 日志里出现：

- `Network is unreachable`
- `TimeoutError`
- `Can't load the configuration of 'Qwen/...`
- health check 持续失败，`backend/frontend` 卡在 `Created`

通常不是业务代码问题，而是 `qwen` 容器拉不到模型。

先跑诊断脚本：

```bash
bash scripts/debug-hf-connectivity.sh
```

优先恢复路径有两条：

1. 服务器可以通过代理或可达镜像访问外网

在 `.env` 里配置：

```bash
CVECT_HF_ENDPOINT=https://huggingface.co
CVECT_HTTP_PROXY=http://<proxy-host>:<port>
CVECT_HTTPS_PROXY=http://<proxy-host>:<port>
CVECT_NO_PROXY=127.0.0.1,localhost,postgres,qwen,backend,frontend
```

如果主站可达但 Xet 下载链路不通，再加：

```bash
CVECT_HF_HUB_DISABLE_XET=true
```

然后只重建 `qwen`：

```bash
docker compose --env-file .env -f docker-compose.yml up -d --force-recreate qwen
```

2. 服务器完全不能访问 Hugging Face

先在一台可联网机器上预下载并打包模型缓存：

```bash
scripts/qwen-offline-cache.sh prefetch
scripts/qwen-offline-cache.sh verify
scripts/qwen-offline-cache.sh pack
```

把 `.artifacts/qwen-hf-cache.tgz` 传到服务器，解包到 `CVECT_HF_CACHE_DIR`：

```bash
scripts/qwen-offline-cache.sh unpack /path/to/qwen-hf-cache.tgz
scripts/qwen-offline-cache.sh verify
docker compose --env-file .env -f docker-compose.yml up -d --force-recreate qwen
```

默认 `.env` 已经把 `CVECT_HF_HUB_OFFLINE=true`、`CVECT_HF_LOCAL_FILES_ONLY=true` 打开，服务器不会再尝试在线下载模型。

`backend/frontend` 本身不需要因为这个问题单独重建；`qwen` healthy 之后，再执行一次：

```bash
scripts/server-run.sh up
```

把剩余服务补起来即可。

## 已知边界

- 当前 README 描述的是本地开发路径，不等同于生产部署方案
- 前端是单页控制台，没有登录、权限或国际化
- `Qwen` 目录里除了在线服务，还包含数据生成脚本和生成产物，不属于运行时必需内容
