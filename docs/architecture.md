# 项目架构

## 当前阶段

阶段 3 已实现 MySQL + Flyway 迁移、用户认证及知识库管理。`knowledge_bases` 以 `user_id` 归属用户，提供当前用户范围内的 CRUD。文档解析、向量化与 RAG 功能尚未实现。

## 目标架构

```text
Vue 3 前端
    │ REST API / SSE（后续）
    ▼
Spring Boot 后端
    ├── 用户与 JWT 认证（已完成基础）
    ├── 知识库管理（已完成）
    ├── 文档管理（后续）
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

- Vue 3：当前提供最小注册/登录与知识库管理界面，并在本地浏览器保存登录 token。
- Spring Boot：当前提供健康检查、认证和受 JWT 保护的知识库 REST API。
- MySQL：当前保存用户、知识库与 Flyway 迁移历史；后续保存文档元数据、对话和 Prompt。
- Qdrant：后续保存文本分片向量和检索元数据。
- LangChain4j：后续封装模型调用、Embedding 与 RAG 流程。

## 配置原则

- 密码、Token 和 API Key 仅通过本地环境变量提供。
- `.env.example` 只提供变量名称和示例，不包含真实凭据。
- MySQL 与 Qdrant 通过 Docker Compose 使用命名卷持久化。
- 业务模块将按功能分包，保持模块化单体架构，避免在早期拆分微服务。
- 用户密码仅以 BCrypt 哈希保存，认证响应不包含密码字段。
- JWT Secret 仅通过 `JWT_SECRET` 环境变量提供；服务不会在源码中保存默认生产密钥。
- `JwtAuthenticationInterceptor` 仅拦截知识库 API，并将经过验证的 JWT userId 写入请求线程上下文；Service 查询始终附加 userId 条件以保证数据隔离。
- 知识库当前使用物理删除。后续引入文档、分片和向量后，删除操作将协调处理关联资源。
