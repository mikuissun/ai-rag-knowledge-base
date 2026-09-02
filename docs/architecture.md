# 项目架构

## 当前阶段

阶段 1 仅建立前后端工程、健康检查、环境变量模板和本地基础设施配置。尚未实现用户、数据库表、文档解析、向量化或 RAG 功能。

## 目标架构

```text
Vue 3 前端
    │ REST API / SSE（后续）
    ▼
Spring Boot 后端
    ├── 用户与权限（后续）
    ├── 知识库与文档（后续）
    ├── 文档解析和文本切分（后续）
    ├── RAG 编排（LangChain4j，后续）
    └── 对话与 Prompt 管理（后续）
       │              │
       ▼              ▼
    MySQL 8        Qdrant
                         │
                         ▼
                 通义千问 / DeepSeek（后续）
```

## 组件职责

- Vue 3：提供管理页面和聊天界面；当前仅包含初始化首页。
- Spring Boot：提供 REST API；当前提供 `GET /api/health`。
- MySQL：后续保存用户、知识库、文档元数据、对话和 Prompt。
- Qdrant：后续保存文本分片向量和检索元数据。
- LangChain4j：后续封装模型调用、Embedding 与 RAG 流程。

## 配置原则

- 密码、Token 和 API Key 仅通过本地环境变量提供。
- `.env.example` 只提供变量名称和示例，不包含真实凭据。
- MySQL 与 Qdrant 通过 Docker Compose 使用命名卷持久化。
- 业务模块将按功能分包，保持模块化单体架构，避免在早期拆分微服务。
