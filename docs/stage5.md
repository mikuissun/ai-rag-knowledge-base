# Stage 5：文本切分与 Embedding

本阶段实现 `documents.content_text → document_chunks → chunk_embeddings`，向量只在 MySQL 中暂存和验证。没有实现 Qdrant、向量检索、Retriever、RAG、LLM、SSE、Agent 或 MCP，也没有修改前端。

## 数据库与状态

Flyway V4 不修改旧 migration，执行以下变化：

- documents 增加 processing_status、processing_error、processed_at；原 status 字段及其 Stage 4 含义不变。
- 新建 document_chunks，保存 user_id、knowledge_base_id、document_id、稳定的 chunk_index、content、char_count、token_estimate、embedding_status 和时间戳。
- 新建 chunk_embeddings，保存 chunk_id、model_name、dimension、JSON float 数组和创建时间。
- document_chunks 具备 document_id、knowledge_base_id、user_id、(document_id, chunk_index)、(user_id, knowledge_base_id) 索引，其中文档与序号组合唯一。
- Chunk 使用 (document_id, user_id, knowledge_base_id) 复合外键约束归属；删除 documents 时级联删除 Chunk，再级联删除 Embedding，不会留下孤立记录。

处理状态为 PENDING、PROCESSING、PROCESSED、FAILED。新上传文档默认 PENDING；失败原因只保存可公开错误，不保存 API 响应或密钥。

## 文本切分

TextChunker 使用确定性的字符窗口：

1. 统一 CRLF/CR 为 LF，并去除首尾空白。
2. 在 chunk-size 范围内依次优先寻找段落空行、换行、中文/英文句末标点。
3. 后 45% 范围仍无自然边界时，按字符上限兜底。
4. 下一块从上一块结束位置向前回退 chunk-overlap；只跳过 overlap 起点的空白。
5. 丢弃纯空白块，按 0 开始保存稳定 chunk_index。
6. token_estimate 只是容量规划估算：中日韩字符近似每字符一个 token，其余非空白字符约每四个一个 token；并非模型计费的精确 tokenizer 结果。

默认配置：

| 环境变量 | 默认值 | 约束 |
| --- | ---: | --- |
| RAG_CHUNK_SIZE | 1000 | 100～10000 |
| RAG_CHUNK_OVERLAP | 150 | 大于等于 0 且小于 chunk-size |
| RAG_MAX_CHUNKS_PER_DOCUMENT | 500 | 1～10000 |

配置不合法时应用启动失败，避免运行中产生不一致数据。

## Embedding

生产实现为 EmbeddingService → DashScopeEmbeddingService，使用 JDK HttpClient 调用 OpenAI 兼容的 `/embeddings` 接口，没有把供应商细节泄露到处理服务。

- 默认模型：text-embedding-v4。
- 默认维度：1024，写入每条 chunk_embeddings.dimension。
- 默认批大小：10；长文档被拆成多个有界请求，绝不会一次无限提交。
- API Key：只读取 DASHSCOPE_API_KEY；缺失时处理接口返回 503 并把文档标记 FAILED。
- Base URL：默认中国大陆公共地址，可通过 DASHSCOPE_BASE_URL 配置。生产环境建议改成 API Key 所属地域/业务空间的专属域名。
- 连接/响应超时默认 10/60 秒，可通过环境变量调整。
- 校验响应数量、index 唯一性、向量维度及 NaN/Infinity；不把完整向量返回 API。

阿里云官方资料说明 text-embedding-v4 支持中文等 100+ 语言，单次最多 10 条，1024 维是通用场景的推荐平衡点：<https://help.aliyun.com/zh/model-studio/embedding>。Base URL 必须和 API Key 地域一致：<https://help.aliyun.com/en/model-studio/base-url>。

本阶段保留项目自己的 EmbeddingService 边界，便于测试替换和未来接入其他实现。没有为了单个 HTTP 调用额外引入 SDK；后续使用 LangChain4j 做 Retriever/RAG 时，可以在不改变处理业务和数据库结构的情况下增加适配器。

## API 和完整数据流

新增接口：

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/process
Authorization: Bearer <JWT>
```

请求无 body，也不接受 userId。成功响应 data：documentId、chunkCount、embeddingCount、embeddingModel、processingStatus，不返回向量。

完整流程：

```text
JWT → CurrentUserContext userId
    → 校验 knowledgeBaseId + userId
    → 校验 documentId + knowledgeBaseId + userId
    → 短事务抢占状态 PROCESSING
    → 切分 content_text
    → 分批调用 Embedding（事务外）
    → 校验数量和维度
    → 单事务删除旧 Chunk（Embedding 级联删除）
             + 写入新 Chunk/Embedding
             + 状态改为 PROCESSED
```

同一文档可重复处理。Embedding 生成成功前不会删除旧数据；最终事务原子替换，因此不会重复累积，也不会把半批新数据暴露出来。Embedding 或落库失败时状态改为 FAILED；若此前处理成功，旧 Chunk/Embedding 保留，后续检索只能消费 PROCESSED 文档。

PROCESSING 状态通过条件更新防止同一文档并发重复处理；第二个请求返回 409。数据库事务不包住外部 HTTP 等待。

## 本地配置与调用

`.env.example` 只有模板。Spring Boot 不自动读取项目根目录 `.env`，请在启动后端的同一终端或 IDE 中设置真实变量，且不要提交：

```powershell
$env:DASHSCOPE_API_KEY = "<你的百炼 API Key>"
$env:DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:DASHSCOPE_EMBEDDING_MODEL = "text-embedding-v4"
$env:DASHSCOPE_EMBEDDING_DIMENSION = "1024"
$env:RAG_CHUNK_SIZE = "1000"
$env:RAG_CHUNK_OVERLAP = "150"
$env:RAG_MAX_CHUNKS_PER_DOCUMENT = "500"
```

先按 Stage 4 上传有正文的文档，再执行：

```powershell
$token = "<登录返回的 JWT>"
$kbId = 1
$documentId = 1
curl.exe -sS -X POST `
  "http://localhost:8080/api/knowledge-bases/$kbId/documents/$documentId/process" `
  -H "Authorization: Bearer $token"
```

用户 A 处理用户 B 的知识库或文档、documentId 与 knowledgeBaseId 不匹配时均返回 404。缺少 Key 返回 503；供应商调用或响应校验失败返回 502；并发重复处理返回 409。

## 事务、删除与恢复边界

- PROCESSING 是独立短事务，外部 API 调用不会长期持有数据库事务或行锁。
- 新数据在单个事务中替换旧数据；任何 Chunk、Embedding 或状态写入失败都会整体回滚。
- 文档删除仍沿用 Stage 4 的文件重命名补偿流程；删除 documents 记录时数据库外键在同一事务内清理两层子表。数据库删除失败时，原文件仍按 Stage 4 逻辑恢复。
- 进程在 PROCESSING 后异常退出时文档可能保持 PROCESSING，需要重新启动后人工将其改为 FAILED 才能重试。生产版本应增加 processing_started_at、租约/超时恢复任务或异步队列。
- MySQL 中的 JSON 文本向量只用于 Stage 5 验证，不执行相似度检索。Stage 6 应将向量同步至 Qdrant，并设计一致性和重试策略。

## 测试

自动测试绝不访问真实 DashScope：

- TextChunkerTest：中文、自然边界、overlap、空文本、短文本、顺序和数量上限。
- DashScopeEmbeddingServiceTest：本机 HTTP 假服务验证批大小、响应排序、鉴权头、缺 Key 和非法响应。
- DocumentProcessingIntegrationTest：使用 Spring 测试 Fake Embedding 和真实 MySQL，验证落库、数量一致、幂等、用户隔离、知识库不匹配、FAILED 状态和删除级联。
- 全量 mvn test 同时回归 Stage 1～4。

执行：

```powershell
cd backend
mvn test
```

集成测试沿用当前项目方式连接环境变量指定的 MySQL；测试只删除自己随机创建的数据。没有真实 API Key 时也能运行全部自动测试。

本次验证（2026-09-11）：Java 17.0.18 下 `mvn test` 共 39 项，失败、错误、跳过均为 0；`git diff --check` 通过。Flyway V4 已在 Docker MySQL 8.4 开发数据库完成迁移。前端源代码没有变化，因此未重复执行前端构建。
