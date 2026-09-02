# 企业级 AI 知识库问答系统

用于求职作品集的企业级 AI 应用项目。项目将支持企业知识库管理、文档解析、向量检索、RAG 问答、多轮对话、SSE 流式回答和引用来源展示。

> 当前处于阶段 1：项目工程初始化。用户鉴权、知识库、文档处理和 AI 问答均为后续计划，尚未实现。

## 技术栈

- 后端：Java 17、Spring Boot 3、Maven、MyBatis-Plus、MySQL 8、Flyway
- 前端：Vue 3、TypeScript、Vite、Element Plus（后续接入）
- AI（后续接入）：LangChain4j、通义千问、DeepSeek
- 向量数据库（后续接入）：Qdrant
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

1. 复制 `.env.example` 为本地 `.env`，并填写数据库密码；`.env` 不会被 Git 跟踪。
2. 设置数据库相关环境变量后，在 `backend` 目录执行：

```bash
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/api/health`

阶段 1 的后端可以在数据库尚未准备好时启动；数据库迁移将在后续基础架构阶段启用。

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
