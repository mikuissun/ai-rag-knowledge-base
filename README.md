# 企业级 AI 知识库 RAG 系统

## 项目简介

基于 Spring Boot、Vue、Qdrant 和通义千问构建的企业知识库 RAG 问答系统。

系统支持从文档上传、解析、向量化、检索到问答生成的完整链路，并通过 Web UI 提供知识库管理、文档处理和流式问答能力。

## 核心功能

- JWT 用户认证
- 用户数据隔离
- 知识库 CRUD
- PDF / DOCX / Markdown / TXT 文档上传与解析
- Chunk + Overlap 文本切分
- Embedding 向量化
- Qdrant 向量检索
- RAG 问答
- Citation 结构化来源
- SSE 流式回答
- AbortController 停止生成
- Prompt Injection 基础防护
- 完整 Web UI

## 系统架构

~~~mermaid
flowchart LR
    UI[Vue 3 Web UI] --> API[Spring Boot REST / SSE]
    API --> DB[(MySQL)]
    API --> EMB[DashScope Embedding]
    API --> VDB[(Qdrant)]
    API --> LLM[qwen-plus]
~~~

## RAG 工作流程

~~~mermaid
flowchart LR
    D[Document] --> P[Parser] --> C[Chunk] --> E[Embedding] --> V[(Qdrant)]
    Q[Question] --> QE[Query Embedding] --> R[TopK Retrieval]
    V --> R
    R --> CTX[Context Prompt] --> L[qwen-plus] --> OUT[SSE Answer + Citation]
~~~

## 技术栈

| 分类 | 技术 |
| --- | --- |
| Backend | Java 17、Spring Boot 3、Maven、MyBatis-Plus、JWT |
| Frontend | Vue 3、TypeScript、Vite、Element Plus、Axios、Vue Router |
| AI | DashScope text-embedding-v4、通义千问 qwen-plus |
| Vector Database | Qdrant 1.14.1、官方 Java Client |
| Database | MySQL 8、Flyway |
| Infrastructure | Docker Desktop、Docker Compose、本地文件系统 |

## 项目亮点

- 不信任前端传入的 userId，用户身份统一从已验证的 JWT 获取。
- MySQL 业务查询与 Qdrant payload filter 同时执行用户级隔离。
- 文档处理状态与向量索引状态分离管理，便于重试和定位失败环节。
- 外部 Embedding、Qdrant 和 LLM 调用不放在长时间数据库事务中。
- Citation 以结构化数据返回文档、Chunk、相似度和摘要信息。
- SSE 配合 fetch + ReadableStream 实现流式回答，使用 AbortController 支持停止生成。
- Prompt 对知识库内容与系统指令进行边界区分，提供基础 Prompt Injection 防护。
- 检索不到足够相关资料时拒绝自由生成，明确告知当前知识库无法确定。

## 功能展示

### 知识库管理

<!-- screenshot -->

### 文档处理与索引

<!-- screenshot -->

### RAG 问答

<!-- screenshot -->

### Citation 来源

<!-- screenshot -->

## 快速开始

### 1. 准备环境变量

在项目根目录执行：

~~~powershell
Copy-Item .env.example .env
~~~

编辑根目录 .env，填写本地数据库、JWT 和 DashScope 配置。真实密钥只保存在本地 .env，不要提交到 Git。

### 2. 启动基础服务和后端

确认 Docker Desktop 已启动，然后在项目根目录运行：

~~~powershell
.\scripts\dev-start.ps1
~~~

该脚本会读取根目录 .env，启动 Docker Compose 中的 MySQL 和 Qdrant，并运行后端 Spring Boot 服务。

### 3. 启动前端

另开终端：

~~~powershell
cd frontend
npm install
npm run dev
~~~

前端默认地址为 http://localhost:5173，后端默认地址为 http://localhost:8080。开发环境下 Vite 代理将 /api 请求转发到后端。

如需单独配置前端 API 地址，可参考 frontend/.env.example 创建 frontend/.env。该文件不应提交。

## 环境变量

根目录 .env 的主要配置包括：

~~~text
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
JWT_SECRET
DASHSCOPE_API_KEY
~~~

.env.example 只包含变量模板，不包含真实密码或 API Key。前端 VITE_* 配置属于公开构建配置，不应放入后端密钥。

## 核心 API

- POST /api/auth/register
- POST /api/auth/login
- GET/POST /api/knowledge-bases
- GET/PUT/DELETE /api/knowledge-bases/{knowledgeBaseId}
- GET/POST /api/knowledge-bases/{knowledgeBaseId}/documents
- POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/process
- POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/index
- POST /api/knowledge-bases/{knowledgeBaseId}/chat
- POST /api/knowledge-bases/{knowledgeBaseId}/chat/stream

所有受保护接口均使用 Authorization: Bearer <JWT>，并由后端校验当前用户与资源归属。

## 项目结构

~~~text
.
├── backend/      # Spring Boot 后端
├── frontend/     # Vue 3 前端
├── deploy/       # Docker Compose 基础设施配置
├── docs/         # 架构、开发计划与阶段文档
├── scripts/      # 本地开发启动脚本
├── .env.example  # 环境变量模板
└── README.md
~~~

## 测试与验证

- Backend 61 个测试场景已验证。
- Qdrant 集成测试通过。
- Frontend build 通过。
- 真实 DashScope + Qdrant + SSE + Citation 链路已手动验证。
- 验证范围包括文档上传、解析、Chunk、Embedding、Qdrant Index、Query Embedding、TopK Retrieval、Prompt、qwen-plus、SSE 和 Citation。

以上为本地开发和端到端联调验证，不等同于生产环境验证。

## 当前边界

- 当前为单轮 RAG，暂未提供持久化多轮对话历史。
- 当前主要使用向量检索。
- 极短查询可能存在召回不足。
- 未实现 Hybrid Search。
- 未实现 Rerank。
- 未实现 Agent、MCP、Multi-Agent。

## 文档

- [系统架构](docs/architecture.md)
- [开发计划](docs/development-plan.md)
- [Stage 1](docs/stage1.md)
- [Stage 2](docs/stage2.md)
- [Stage 3](docs/stage3.md)
- [Stage 4](docs/stage4.md)
- [Stage 5](docs/stage5.md)
- [Stage 6](docs/stage6.md)
- [Stage 7](docs/stage7.md)
- [Stage 8](docs/stage8.md)
