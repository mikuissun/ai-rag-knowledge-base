# 项目架构

## 当前阶段

Stage 1～8 已完成：用户认证、用户隔离的知识库与文档管理、文本切分与 Embedding、Qdrant 索引和检索、单轮 RAG、引用及 SSE 前端工作区。提交对应关系见 [开发计划](development-plan.md)。

## 当前架构

```text
Vue 3 + TypeScript + Vite + Element Plus
    │ REST API（Axios / fetch）及 POST SSE（fetch ReadableStream）
    ▼
Spring Boot 3 / Java 17 / Maven：模块化单体
    ├── auth / user：注册、登录、JWT 与请求身份
    ├── knowledge：知识库 CRUD 与归属校验
    ├── document：上传、解析、切分、处理与索引协调
    ├── EmbeddingService：文档 / 查询向量化
    ├── VectorStoreService：Qdrant 写入、过滤检索与删除
    └── RAG / ChatModelService：上下文、引用、Qwen 与 SSE
         │
         ├── MySQL：用户、知识库、文档、Chunk、暂存向量与状态
         ├── 本地文件系统：原始上传文件
         ├── Qdrant：向量及检索 payload
         └── DashScope：text-embedding-v4 / qwen-plus
```

模型由后端调用，Qdrant 不负责生成答案。当前没有 LangChain4j 依赖、DeepSeek 适配器或 Spring Security FilterChain，早期技术设想不等于已实现组件。

## 组件职责

- 前端：Vue Router 懒加载页面，Vue 响应式身份状态与 localStorage 持久化；展示文档操作、聊天和来源。普通 REST 主要使用 Axios，RAG 使用 fetch，SSE 使用流式 UTF-8 解码和 AbortController。
- 后端：Spring Boot 3.5.0、Java 17、MyBatis-Plus 3.5.11；按功能分包，复用 `ApiResponse`、`BusinessException` 和统一异常处理。
- MySQL：Compose 使用 MySQL 8.4；Flyway V1～V5 管理用户、知识库、文档、Chunk、暂存向量及处理 / 索引状态。MySQL 不承担相似度检索。
- 文件系统：按用户和知识库组织文件，使用 UUID 文件名；PDFBox / POI 解析 PDF / DOCX，Markdown / TXT 读取文本，正文存入 MySQL。
- Embedding：`DashScopeEmbeddingService` 使用 JDK HttpClient 调用 DashScope OpenAI-compatible API，默认 `text-embedding-v4`、1024 维。
- Qdrant：Compose 与 Java 客户端均为 1.14.1；客户端通过 gRPC 6334 访问，6333 为 HTTP。默认 collection `knowledge_chunks`、1024 维、Cosine。
- LLM：`DashScopeChatModelService` 使用 JDK HttpClient 调用 Qwen，默认 `qwen-plus`，支持普通及流式回答，与 RAG 编排解耦。

## 核心数据流

### 文档准备

`当前用户 → 知识库归属校验 → 上传原文件 → 解析正文 → documents`

`/process → 正文 → TextChunker → Embedding → 事务保存 Chunk 与暂存向量`

`/index → 校验归属和向量 → 清理文档旧 Point → 批量 upsert → 更新索引状态`

默认切分长度 1000、重叠 150，可配置。Point 使用稳定 Chunk 标识，payload 包含 `chunkId`、`documentId`、`knowledgeBaseId`、`userId`、`chunkIndex`、`content`，重复同步不会无限增加同一 Chunk 的 Point。

### 检索与问答

`用户与知识库校验 → 问题 Embedding → Qdrant TopK → 分数与长度筛选 → Prompt → Qwen → 答案 / sources`

检索同时按 `userId + knowledgeBaseId` 过滤；RAG 批量读取数据库文档信息补充来源名称并验证关联，避免逐条查询。没有有效上下文时直接拒答。

普通接口返回答案与来源；SSE 发送 `message`、`sources`、`done`、`error`。Citation 表示提供给模型的参考片段，不等同于独立事实核验。

## 一致性与删除

- 数据库事务不能回滚文件系统或 Qdrant；上传失败清理文件，文档删除保留文件补偿策略。
- 文档删除协调 Qdrant Point、暂存向量、Chunk、文档记录和原文件。Qdrant 清理失败时阻止继续删除数据库。
- Qdrant 删除与数据库提交不是分布式事务；前者成功、后者失败时可能需要重新索引恢复，不能声称全链路原子删除。
- 重处理需要使旧索引失效并处理旧 Point；重新索引是文档范围删除后批量写入，不是原子替换，失败后需要重试恢复。
- 知识库存在文档时不直接级联删除，应先删除文档；数据库外键不能清理本地文件和向量。
- 外部模型请求不包在长数据库事务中。具体补偿与失败边界见 [Stage 4](stage4.md)、[Stage 5](stage5.md)、[Stage 6](stage6.md)。

## 安全与配置原则

- 密码只存 BCrypt 哈希；JWT Secret、DashScope Key 和数据库密码来自环境变量，不写入源码。
- JWT 拦截器保护 `/api/knowledge-bases` 及子路径，通过 `CurrentUserContext` 提供身份；同步结束和 SSE 异步切换时清理 ThreadLocal，异步任务显式携带已校验的用户标识。
- Service 复用归属校验；文档查询限制文档、知识库和用户，向量查询限制用户及知识库。不信任前端 userId；部分更新 / 删除是在归属查询后按主键执行，不应误写为所有 SQL 都附带 userId。
- 文件限制类型和大小，原始文件名不决定存储路径；上下文作为不可信参考资料，并受 topK、最低分数和长度约束。
- 根目录 `.env` 被 Git 忽略，`scripts/dev-start.ps1` 读取并注入启动进程；模板不存真实凭据。前端 `VITE_*` 是公开配置，禁止存后端密钥。
- MySQL 保持 `3306:3306`，Qdrant 提供 6333 / 6334，Compose 命名卷持久化数据。本地启动需避免端口冲突，不用删除 volume 解决配置问题。

## 当前边界

单轮问答不包含持久化聊天历史、多轮 Memory、Prompt CRUD、Agent、Tool Calling、MCP、Multi-Agent、Hybrid Search 或 Rerank。前端临时消息不是数据库会话系统；Health API 不是数据库、Qdrant 与模型的完整就绪探针。

目前面向本地开发与项目展示，真实联调记录见 [Stage 8](stage8.md)，不等于持续生产可用性保证。本次文档更新没有重新执行外部模型调用。
