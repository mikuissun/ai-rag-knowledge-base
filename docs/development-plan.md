# 开发计划与完成状态

## 当前进度

Stage 1～8 已完成。本文按实际 Git 历史整理，不再将文档处理、向量检索、RAG、SSE 和完整前端列为待开发功能。

| 阶段 | 已完成内容 | 对应提交 |
| --- | --- | --- |
| [Stage 1](stage1.md) | Java 17 / Spring Boot 3 / Maven、MyBatis-Plus 依赖、MySQL / Qdrant Compose、Health API、基础结构 | `751a605` |
| [Stage 2](stage2.md) | User 实体、Flyway V1、注册登录、BCrypt、JWT 签发与解析、最小认证前端 | `cd8de14` |
| [Stage 3](stage3.md) | Flyway V2、知识库 CRUD、JWT 拦截、CurrentUserContext、用户隔离及最小管理界面 | `868dc8a` |
| [Stage 4](stage4.md) | Flyway V3、PDF / DOCX / Markdown / TXT 上传、保存、解析、查询删除及文件补偿 | `23ebd7c` |
| [Stage 5](stage5.md) | Flyway V4、文本切分、Embedding、MySQL 暂存 Chunk / 向量、处理状态及重处理 | `1cabbe2` |
| [Stage 6](stage6.md) | Flyway V5、Qdrant 初始化、批量索引、隔离过滤、相似度检索与删除联动 | `307e820` |
| [Stage 7](stage7.md) | 单轮 RAG、Prompt 构建、阈值和上下文限制、引用、普通 / SSE 问答及异步身份清理 | `3aca7ae` |
| [Stage 8](stage8.md) | Vue 工作区、认证及知识库 UI、文档操作、POST SSE、Citation、Abort、错误与空状态 | `d258450` |

## 已完成的配套优化

- `a2d14d5 chore: improve local development startup`：PowerShell 读取根目录 `.env`，启动 Compose 并运行后端。
- `3d61878 style: polish rag frontend experience`：视觉层级、中文状态、Chat / Citation、响应式与低风险组件拆分。
- 后端测试和前端构建 / 浏览器验证范围见各阶段文档；真实服务联调记录及证据边界见 Stage 8。本次仅整理文档，不代表重新执行全部回归或真实模型调用。

## 阶段归属说明

文档既解释当前能力，也注明首次加入的提交。User 与注册登录首次加入 Stage 2；JWT 拦截器、`CurrentUserContext` 和 `WebMvcConfig` 首次加入 Stage 3；SSE 异步线程上下文清理在 Stage 7 补充。不能把当前完整实现全部归入 Stage 1。

当前认证使用 MVC 拦截器，不是 Spring Security FilterChain。模型适配通过项目自己的服务接口和 JDK HttpClient 实现，未引入 LangChain4j。原计划中的“Stage 4 认证过滤器”“Stage 10 SSE”“Stage 11 完整前端”不再作为当前阶段编号使用。

`/process` 与 `/index` 保持分离；当前 Word 支持 DOCX，不支持旧版 DOC。已完成的单轮问答不包含聊天历史或 Prompt CRUD。

## 未实现事项与后续候选

以下仅记录边界，不表示已经实现，也不表示本次开始开发：

- 多轮会话、聊天历史持久化和历史查询。
- Prompt 版本与管理界面。
- 持续集成、可重复浏览器端到端测试、生产部署、监控和跨存储故障恢复验证。
- 更深入的性能与构建体积优化、展示材料和部署文档完善。

Agent、Tool Calling、MCP、Multi-Agent、Hybrid Search、Rerank 不属于已完成的 Stage 1～8；是否开展任何新功能应另行确认。
