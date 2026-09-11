# Stage 8：知识库前端与 UI / UX 精修

## 目标

将后端能力串成可操作、可演示的工作区：

`登录 → 知识库 → 上传 → process → index → RAG Chat → SSE 与 Citation`

功能提交：`d258450 feat: implement rag knowledge base frontend`；后续精修：`3d61878 style: polish rag frontend experience`。本阶段复用 Stage 1～7 API，没有新增后端业务模块或数据库 migration。

## 实现内容

- Vue 3 + TypeScript + Vite + Element Plus，Vue Router 页面级懒加载。
- 登录注册、JWT 持久化、路由守卫、401 自动退出。
- 知识库列表、创建、编辑、删除、详情及空状态。列表使用已有更新时间，详情展示加载到的文档数量，不虚构列表统计字段。
- PDF / DOCX / Markdown / TXT 上传及 20 MiB 基础校验；process 和 index 仍为两个独立操作，支持重新处理、重新索引和删除。
- 单轮问答、POST SSE、中文流式展示、停止生成、错误提示、参考来源与自动滚动。
- 统一侧栏、页面层级、表单、按钮、加载和空状态；新增状态标签和引用组件，避免重写整个前端。

文档处理状态展示为“待处理 / 处理中 / 已处理 / 失败”，索引状态展示为“待索引 / 索引中 / 已建立索引 / 失败”；忙碌状态有加载提示，失败保留错误信息，不改变后端真实状态值。

## 核心流程

### 登录与文档操作

登录响应中的 Token 和用户信息通过 `stores/auth.ts` 的 Vue 响应式状态及 localStorage 保存，没有引入 Pinia。请求附加 `Authorization: Bearer <token>`；Router guard 只检查本地登录态，实际 Token 有效性和资源权限由后端判断。401 清除身份并返回登录页。

进入知识库详情后加载文档；上传调用后端保存与解析，process 生成 Chunk 和 Embedding，index 将向量写入 Qdrant。准备完成后发送问题，聊天界面当前使用 topK=5。

### SSE、Abort 与滚动

流式请求使用 `fetch` POST 和 `ReadableStream`，不是原生 EventSource。使用流式 UTF-8 解码、未完成事件缓冲和事件分隔符解析，兼容网络分块中的中文字符。

| 事件 | 行为 |
| --- | --- |
| `message` | 追加 `delta` 到回答 |
| `sources` | 保存并展示结构化引用 |
| `done` | 标记完成 |
| `error` | 展示错误并结束生成状态 |

每次生成创建独立 `AbortController`；停止按钮及组件卸载均中止请求，读取结束释放 reader 锁。切换知识库重建页面，避免混入上一知识库临时消息；刷新文档列表不会刻意销毁聊天组件。

用户接近底部时自动跟随回答，向上阅读时不持续强拉到底部。Enter 发送、Shift+Enter 换行，并避开输入法组合输入。浏览器 Abort 不保证供应商已经生成的内容不计费。

### Citation

展示来源数量、文档名、Chunk 编号、相似度及摘要。score 转为百分比仅用于展示，不修改原始数据，也不表示回答正确概率。没有来源时不伪造引用。

## API / 关键文件

| 文件 | 职责 |
| --- | --- |
| `frontend/src/router/index.ts` | 路由、懒加载、登录守卫 |
| `frontend/src/stores/auth.ts` | 身份持久化和清理 |
| `frontend/src/api/http.ts` | Axios 通用请求、Bearer 和 401 |
| `frontend/src/api/rag.ts` | 普通及流式 RAG fetch 请求、SSE 解码 |
| `frontend/src/components/ChatPanel.vue` | 问答、消息、生成和 Abort 生命周期 |
| `frontend/src/components/CitationList.vue` | 引用来源 |
| `frontend/src/components/DocumentStatusTag.vue` | 中文处理 / 索引状态 |

| API | 用途 |
| --- | --- |
| `POST /api/auth/register`、`POST /api/auth/login` | 注册登录 |
| `GET/POST /api/knowledge-bases` | 列表、创建 |
| `GET/PUT/DELETE /api/knowledge-bases/{id}` | 详情、编辑、删除 |
| `GET/POST /api/knowledge-bases/{id}/documents` | 文档列表、上传 |
| `GET/DELETE /api/knowledge-bases/{id}/documents/{documentId}` | 文档详情、删除 |
| `POST /api/knowledge-bases/{id}/documents/{documentId}/process` | 切分与 Embedding |
| `POST /api/knowledge-bases/{id}/documents/{documentId}/index` | Qdrant 索引 |
| `POST /api/knowledge-bases/{id}/chat` | 非流式问答 |
| `POST /api/knowledge-bases/{id}/chat/stream` | 流式问答 |

## 安全设计

- 不由前端决定 userId；后端继续校验知识库归属及 Qdrant 用户过滤。
- localStorage 使用 `knowledge-base-token`、`knowledge-base-user`；退出及 401 清除两者。浏览器 Token 仍有 XSS 风险，不等同于 HttpOnly Cookie。
- 回答与引用按文本展示，不直接执行模型输出的 HTML。
- `DASHSCOPE_API_KEY`、`JWT_SECRET`、MySQL 密码仅属于后端环境，禁止放进前端源码或 `VITE_*`。Vite 变量会进入公开构建产物。
- loading / disabled 防止重复点击，但不替代服务端校验与幂等。
- 无有效知识时展示后端拒答，不在浏览器拼造答案。

## 测试 / 验证

### 本地启动

根目录 `.env` 保存本地后端配置；已有启动脚本在 `a2d14d5` 中加入，不是 Stage 8 新增。

```powershell
# 仓库根目录启动基础服务和后端
.\scripts\dev-start.ps1

# 另开终端
cd frontend
npm install
npm run dev

# TypeScript 与生产构建
npm run build
```

`frontend/.env.example` 的 `VITE_API_BASE_URL` 留空时，开发请求通过 Vite 代理访问 `localhost:8080`；非空时直接请求配置地址，需要相应跨域或同源代理条件。根目录 `.env` 由后端脚本读取，不应假设 Vite 自动加载它。

### 已有记录与证据边界

- 维护者在 Stage 8 提交前确认真实链路成功：上传、process、`text-embedding-v4`、Qdrant index、RAG chat、`qwen-plus`、SSE、Citation、无相关知识拒答和 Stop/Abort。
- UI 精修时构建与 `git diff --check` 通过；浏览器模拟 API 验证覆盖登录态、CRUD、文档操作、中文 SSE、来源、Abort、错误、401 和空状态。
- 精修验证包含 1920、1440、1280、390 像素窗口；未观察到页面级横向溢出，文档表格可在自身容器滚动。
- 当前没有配置 `npm run lint`，不能把未执行的 lint 写成通过。

这些是此前开发与人工联调记录，不是本次整理重新执行的测试。真实外部调用由维护者确认；仓库没有随文档附带完整供应商日志或持久化浏览器端到端测试套件。仅凭 Git 提交不能证明服务当前仍然可用。

## 当前边界

- 页面内消息临时保存，请求不携带完整历史，不等于多轮 Memory 或数据库聊天历史。
- 没有 Prompt 管理页面、Agent、Tool Calling、MCP、Multi-Agent、Hybrid Search 或 Rerank。
- 路由已懒加载，但 Element Plus 等共享依赖仍有较大的主包及构建体积提示，未为消除提示大规模改造依赖导入。
- 移动端提供基本可用布局，主要演示目标是桌面工作区。
- 后端约束见 [Stage 7](stage7.md)，前端不扩展或绕过其权限边界。
