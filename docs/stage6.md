# Stage 6：Qdrant 向量索引与相似度检索

Stage 6 把 Stage 5 已保存在 MySQL 的 Chunk Embedding 同步到 Qdrant，并提供带用户隔离的语义检索 API。本阶段输出相关文本块，不调用 LLM，也不生成最终 RAG 答案。

## Qdrant 的职责

MySQL 继续保存业务实体、Chunk 正文、处理状态和 Embedding 验证副本；Qdrant 专门负责高维向量的近似最近邻检索。MySQL 的普通索引不适合对 1024 维浮点向量执行 Cosine 相似度检索，而 Qdrant 提供向量索引、payload filter、批量 upsert 和稳定 Point ID。

项目使用官方 Java 客户端 `io.qdrant:client:1.14.1`，与 Compose 中的 Qdrant Server `v1.14.1` 对齐。客户端通过 gRPC `6334` 通信；HTTP `6333` 用于管理和健康检查。

## Collection 设计

- 名称：`knowledge_chunks`，可通过 `QDRANT_COLLECTION` 修改
- 向量维度：1024，与 `text-embedding-v4` 配置一致
- 距离算法：Cosine
- 初始化：首次索引、删除或检索时检查；不存在则创建，存在则校验维度与距离算法
- 不兼容配置：返回 409，不会自动删除或重建已有 collection

每个 Chunk 对应一个 Point，直接使用 MySQL `document_chunks.id` 作为数值 Point ID。重复索引同一批 Chunk 会覆盖相同 ID；重新处理导致 Chunk ID 改变时，`/index` 会先删除该文档原有 Points，再写入新 Points。

Payload：

```json
{
  "chunkId": 101,
  "documentId": 20,
  "knowledgeBaseId": 3,
  "userId": 1,
  "chunkIndex": 0,
  "content": "文本块正文"
}
```

检索和删除都使用 payload filter。检索至少同时匹配 `userId` 与 `knowledgeBaseId`，不能只依赖知识库 ID。

## 配置

```dotenv
QDRANT_HOST=localhost
QDRANT_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_COLLECTION=knowledge_chunks
QDRANT_BATCH_SIZE=100
QDRANT_TIMEOUT_SECONDS=15
RAG_SEARCH_TOP_K=5
RAG_MAX_SEARCH_TOP_K=20
```
`QDRANT_PORT` 是 Compose 暴露的 HTTP 端口，Java 客户端使用 `QDRANT_GRPC_PORT`。所有值均从环境变量读取，没有在 Java 中写死部署地址或凭据。

## 启动基础设施

在项目根目录准备本地 `.env`（不要提交），至少设置 `MYSQL_ROOT_PASSWORD`，然后执行：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d mysql qdrant
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

Qdrant HTTP 健康检查：

```bash
curl http://localhost:6333/healthz
```

## 文档索引 API

```http
POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/index
Authorization: Bearer <JWT>
```

PowerShell 示例：

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/knowledge-bases/1/documents/10/index" `
  -Headers $headers
```

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": 10,
    "pointCount": 8,
    "collection": "knowledge_chunks",
    "indexingStatus": "INDEXED"
  }
}
```

完整索引数据流：

1. 从 JWT 的 `CurrentUserContext` 取得 userId。
2. 使用 `knowledgeBaseId + userId` 验证知识库归属。
3. 使用 `documentId + knowledgeBaseId + userId` 验证文档归属及 `PROCESSED` 状态。
4. 短事务将文档状态切换为 `INDEXING`，并阻止并发重复索引或重新处理。
5. 从 MySQL 按 `chunk_index` 读取 Chunk 和 Embedding，验证数量、JSON、有限浮点值及 1024 维度。
6. 在数据库事务之外初始化/校验 collection。
7. 按文档过滤删除旧 Points，再按默认每批 100 个执行 upsert。
8. 短事务更新为 `INDEXED` 并记录 `indexed_at`；失败则更新为 `FAILED` 和安全错误信息。

## 相似度检索 API

```http
POST /api/knowledge-bases/{knowledgeBaseId}/search
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "query": "如何配置企业知识库？",
  "topK": 5
}
```

`topK` 可省略，默认 5，范围为 1～20。curl 示例：

```bash
curl -X POST "http://localhost:8080/api/knowledge-bases/1/search" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"query":"如何配置企业知识库？","topK":5}'
```

结果中的每一项包含 `chunkId`、`documentId`、`chunkIndex`、`content` 和 `score`，不会返回向量数组。

完整检索数据流：

1. 验证 JWT 和当前用户的知识库归属。
2. 使用 Stage 5 的 `EmbeddingService` 将 query 转换为 1024 维向量。
3. 验证查询向量数量与维度。
4. Qdrant 使用 Cosine 检索，并强制应用 `userId + knowledgeBaseId` filter。
5. 返回 TopK payload 与 score。

## 状态与一致性

Flyway V5 为 `documents` 新增：

- `indexing_status`：`PENDING / INDEXING / INDEXED / FAILED`
- `indexing_error`：保存可公开的失败原因，最长 1000 字符
- `indexed_at`：最近一次成功索引时间

重新执行 Stage 5 `/process` 会把状态重置为 `PENDING`，但保留 `indexed_at`，表示 Qdrant 可能仍有上一版 Points；随后执行 `/index` 会原子替换 Qdrant 中该文档的数据。在两步之间，检索可能仍看到上一版索引，这是当前显式两阶段流程的已知边界。

删除已索引、索引中或索引失败的文档时，先删除 Qdrant Points；如果 Qdrant 删除失败，数据库与本地文件保持不变。Qdrant 删除成功后若文件或数据库删除失败，文档记录仍可通过重新执行 `/index` 恢复索引。数据库成功删除后，外键级联清理 `document_chunks` 和 `chunk_embeddings`。

当前知识库删除接口在存在文档时受 MySQL 外键限制并返回 409；必须先逐个删除文档，因此每个文档的 Qdrant Points 都会被清理。

## 故障处理

- Qdrant 未启动、网络失败、创建/upsert/search/delete 失败：返回 503。
- Qdrant 调用超时：返回 504。
- collection 维度或距离算法不兼容：返回 409，保留已有 collection。
- Embedding 缺失或维度错误：索引返回 409，并将文档标记为 `FAILED`。
- 不向客户端泄露 gRPC 地址、内部堆栈或真实 API Key。

## 测试

常规测试使用 Fake Embedding 与内存 VectorStore，不访问真实 DashScope 或 Qdrant：

```bash
cd backend
mvn test
```

真实 Qdrant 集成测试由环境变量显式开启：

```powershell
$env:QDRANT_INTEGRATION_TEST = "true"
$env:QDRANT_HOST = "localhost"
$env:QDRANT_GRPC_PORT = "6334"
mvn test
```

集成测试使用随机 collection，并在结束后删除该测试 collection，不触碰项目的 `knowledge_chunks` collection 或持久化 volume。

## Stage 6 边界

本阶段只有“查询向量 → 相似度检索 → 返回相关 Chunk”，暂不实现 Prompt 拼接、LLM 答案生成、引用编排、SSE、Agent 或 MCP。
