# 企业级 AI 知识库问答系统

用于求职作品集的企业级 AI 应用项目。项目将支持企业知识库管理、文档解析、向量检索、RAG 问答、多轮对话、SSE 流式回答和引用来源展示。

> 当前完成 Stage 6：已具备 JWT 用户隔离、知识库与文档管理、文本切分、Embedding、Qdrant 向量索引和相似度检索。最终 RAG 答案生成仍为后续阶段。

## 技术栈

- 后端：Java 17、Spring Boot 3、Maven、MyBatis-Plus、MySQL 8、Flyway
- 前端：Vue 3、TypeScript、Vite、Element Plus（后续接入）
- AI：DashScope `text-embedding-v4`（LLM 与 LangChain4j 后续接入）
- 向量数据库：Qdrant 1.14.1、官方 Java Client 1.14.1
- 基础设施：Docker Compose

## 当前项目结构

```text
.
├── backend/              # Spring Boot 后端
├── frontend/             # Vue 3 前端
├── deploy/               # Docker Compose 基础设施配置
├── docs/                 # 架构与开发文档
├── .env.example          # 环境变量模板
├── .gitignore
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 20+（建议使用 LTS 版本）
- npm 10+
- Docker Desktop（运行 MySQL 与 Qdrant 时需要）

## 后端启动

1. 启动 Docker MySQL，或准备一个可访问的 MySQL 8 数据库。
2. 在终端设置 MySQL 连接变量与至少 32 字符的 `JWT_SECRET`。处理和检索文档时还需设置 `DASHSCOPE_API_KEY`，并确保 Qdrant 的 HTTP `6333` 与 gRPC `6334` 端口可访问。`.env.example` 仅作为变量模板；Spring Boot 从系统环境变量读取配置。
3. 在 `backend` 目录执行：

```bash
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/api/health`

启动时 Flyway 会执行数据库迁移。认证接口：`POST /api/auth/register`、`POST /api/auth/login`；登录后可通过 `Authorization: Bearer <JWT>` 调用知识库接口。

## 知识库 API

- `POST /api/knowledge-bases`：创建当前用户的知识库
- `GET /api/knowledge-bases`：查询当前用户的知识库列表
- `GET /api/knowledge-bases/{id}`：查询当前用户的知识库详情
- `PUT /api/knowledge-bases/{id}`：修改当前用户的知识库
- `DELETE /api/knowledge-bases/{id}`：删除当前用户的知识库

知识库接口仅从已验证的 JWT 取得当前用户，不接受 `userId` 请求参数。当前版本采用物理删除；含文档的知识库返回 409，请先删除文档。

## Stage 4 文档管理

已提供 PDF / DOCX / Markdown / TXT 上传、原文件保存、正文解析、文档列表/详情及删除 API。单文件上限 20MiB，默认存储目录为后端工作目录下的 `data/uploads`，可通过 `STORAGE_BASE_PATH` 配置；Flyway V3 新建 documents 表。

接口位于 `/api/knowledge-bases/{knowledgeBaseId}/documents`，沿用 JWT 与用户数据隔离。Stage 4 本身不包含文本切分、Embedding 或 RAG。完整变更清单、启动命令、curl 示例、安全边界及测试说明见 [Stage 4 文档](docs/stage4.md)。

## Stage 5 文本切分与 Embedding

已新增文档处理接口 `POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/process`。正文按自然边界切分后，通过 DashScope `text-embedding-v4` 生成 1024 维向量；Chunk 和向量暂存 MySQL，供下一阶段接入向量数据库。

默认切分参数为 1000 字符、150 字符 overlap、单文档最多 500 个 Chunk。处理配置、事务设计、状态流转、测试方式和已知边界见 [Stage 5 文档](docs/stage5.md)。

## Stage 6 Qdrant 向量索引与检索

处理完成后调用 `POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/index`，将 MySQL 中暂存的 Chunk Embedding 幂等写入 Qdrant。调用 `POST /api/knowledge-bases/{knowledgeBaseId}/search` 可将查询文本向量化，并按 `userId + knowledgeBaseId` 双重过滤返回最相关的 Chunk。

统一 collection 为 `knowledge_chunks`，使用 1024 维稠密向量和 Cosine 距离；默认 `topK=5`、最大 20。配置、payload、索引/检索数据流、故障策略和 curl 示例见 [Stage 6 文档](docs/stage6.md)。Stage 6 只返回检索结果，不生成 RAG 答案。

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：`http://localhost:5173`

## Docker 启动

Docker Desktop 安装并启动后，在项目根目录配置 `MYSQL_ROOT_PASSWORD`，再执行：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

该配置会启动 MySQL 8 与 Qdrant，并使用命名卷持久化数据。

## 后续开发计划

项目将按阶段推进：后端基础架构、用户与权限、知识库管理、文档解析、文本切分与 Embedding、向量检索、RAG 问答、多轮对话、SSE 流式输出、前端页面、测试优化与部署。详见 [开发计划](docs/development-plan.md)。
