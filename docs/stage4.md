# Stage 4：文档上传、存储、解析与管理

本阶段只实现后端文档 API；沿用现有 Vue 页面，不新增文档管理 UI。不包含 Chunk、Embedding、向量检索或 RAG。

## 变更清单

新增后端 document 模块：

- controller/DocumentController.java：四个 REST API。
- service/DocumentService.java、DocumentServiceImpl.java：权限、上传、查询、事务与补偿。
- service/LocalDocumentStorage.java：存储根目录、路径校验和有界文件写入。
- mapper/DocumentMapper.java、entity/Document.java：MyBatis-Plus 持久化。
- dto/DocumentResponse.java：详情及正文；DocumentListResponse.java：不带正文的列表元数据。
- parser/DocumentParser.java、DocumentParserFactory.java：统一接口与策略选择。
- parser/PdfDocumentParser.java、WordDocumentParser.java、MarkdownDocumentParser.java、TextDocumentParser.java。
- parser/LimitedTextWriter.java：解析文本上限。
- V3__create_documents_table.sql：新表和索引，不修改 V1、V2。
- DocumentServiceTest.java、DocumentIntegrationTest.java：独立测试和真实 MySQL/HTTP 测试。

修改旧文件：backend/pom.xml（PDFBox 3.0.7、POI OOXML 5.5.1；排除与 Spring JCL 重复的 commons-logging）、KnowledgeBaseApplication.java（Mapper 扫描）、GlobalExceptionHandler.java（上传错误统一响应、通用数据冲突消息）、application.yml（上传限制及存储路径）、.env.example（无密钥的路径示例）、README.md（阶段说明）。

JWT、CurrentUserContext、知识库 Controller/Service、旧迁移、Docker Compose 和前端源代码不变。

## 数据库

Flyway V3 新建 documents 表：id、knowledge_base_id、user_id、original_name、stored_name、file_path、file_type、file_size、content_text、status、created_at、updated_at。

- content_text 为 LONGTEXT，status=1 表示解析成功；解析失败不插入记录。
- file_path 保存相对于存储根目录的路径，不是客户端文件路径。
- 索引：user_id、knowledge_base_id、(user_id, knowledge_base_id)。
- 外键 documents.knowledge_base_id → knowledge_bases.id，ON DELETE RESTRICT。含文档的知识库删除返回 409；先删除文档再删除知识库，避免遗留原文件。并发删除知识库时，外键也防止上传产生孤立记录。
- 用户归属由服务查询条件校验，不依赖客户端提交的 userId。

## 启动

需要 Java 17、Maven、Docker MySQL 或自行配置的 MySQL。先确保宿主机 3306 不被其他服务占用。

在项目根目录打开 PowerShell，配置当前会话环境变量（请替换为自己的值，勿提交真实密码）：

```powershell
$env:JAVA_HOME = "D:\jdk17" # 改成你的 JDK 17 路径
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MYSQL_ROOT_PASSWORD = "<本地容器初始化密码>"
$env:MYSQL_HOST = "localhost"
$env:MYSQL_PORT = "3306"
$env:MYSQL_DATABASE = "ai_knowledge_base"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = $env:MYSQL_ROOT_PASSWORD
$env:JWT_SECRET = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
$env:STORAGE_BASE_PATH = "./data/uploads"
docker compose -f deploy/docker-compose.yml up -d mysql
cd backend
mvn spring-boot:run
```

已有 MySQL 数据卷须使用其原来的密码；改变 MYSQL_ROOT_PASSWORD 不会重置已有数据库密码。不要用 down -v 解决凭据问题。
Spring Boot 不会自动读取根目录 .env；通过当前会话或 IDE 注入环境变量，无 dotenv 依赖。JWT_SECRET 至少 32 字节，更换后已有 JWT 失效。此阶段不需要任何 LLM API Key。

从 backend 目录启动时默认保存到 backend/data/uploads/{userId}/{knowledgeBaseId}/{uuid}.{extension}。uploads/ 已在 .gitignore 中。若自定义路径，务必放在 Git 仓库外，或单独确认忽略规则。不要把上传目录映射为静态资源。

前端在另一终端运行：进入 frontend，执行 npm install、npm run dev。当前上传验证使用下列 API，不需要前端新增页面。

## API 与 PowerShell 验证

先通过现有注册/登录接口获取自己的 JWT，并创建一个自己的知识库。以下 $kbId 必须替换为真实 ID，$filePath 为已存在文件：

```powershell
$token = "<登录返回的 JWT>"
$kbId = 1
$api = "http://localhost:8080/api/knowledge-bases/$kbId/documents"
$filePath = "C:\samples\example.pdf"

# 上传：不要自行设置 multipart Content-Type，curl 自动带 boundary
$uploaded = curl.exe -sS -X POST $api -H "Authorization: Bearer $token" -F "file=@$filePath" | ConvertFrom-Json
$uploaded
$documentId = $uploaded.data.id

# 列表：不含 contentText
curl.exe -sS $api -H "Authorization: Bearer $token"
# 详情：包含 contentText，不暴露 filePath/storedName
curl.exe -sS "$api/$documentId" -H "Authorization: Bearer $token"
# 删除：记录和原始文件清理，返回 code=200
curl.exe -sS -X DELETE "$api/$documentId" -H "Authorization: Bearer $token"
# 再次查询应为 404
curl.exe -sS "$api/$documentId" -H "Authorization: Bearer $token"
```

分别替换为 .pdf、.docx、.md、.txt 重复上传；TXT/Markdown 必须 UTF-8。DOC 不支持。

| 方法 | 路径（前缀 /api/knowledge-bases/{knowledgeBaseId}） | 请求与响应 |
| --- | --- | --- |
| POST | /documents | multipart file；200 元数据和正文 |
| GET | /documents | 200 元数据数组，无正文 |
| GET | /documents/{documentId} | 200 元数据和正文 |
| DELETE | /documents/{documentId} | 200，缺失文档 404 |

负向测试：不带 JWT → 401；其他用户的知识库/文档 → 404；documentId 配错误知识库 → 404；空文件/损坏文档/不允许扩展名/MIME 不匹配 → 400；超过 20MiB → 413。所有响应复用 ApiResponse。

## 数据流、安全与一致性

JWT 拦截器 → CurrentUserContext → 复用知识库归属校验 → 文件非空/大小/扩展名/MIME 校验 → 安全原始名与 UUID 存储名 → 保存原文件 → 策略解析 → 独立数据库事务写入 → 返回 DTO。

- 身份只从 CurrentUserContext 获取，前端 userId 无效。
- 文档详情和删除 SQL 都匹配 documentId + knowledgeBaseId + userId；列表匹配 knowledgeBaseId + userId。
- 原始文件名去路径并限制长度和危险字符；存储路径由可信用户 ID、知识库 ID 和服务端 UUID 构成。
- 拒绝路径逃逸和已有符号链接；上传目录应由应用独占，不能让不可信本机用户并发改目录。
- Multipart 单文件 20MiB、请求 21MiB；服务再次限制大小，实际复制也计数。
- MIME 只是辅助：允许缺失或 application/octet-stream，最终由 PDFBox/POI/严格 UTF-8 解码判断内容。
- 正文最多 500 万字符；空白正文、无文本扫描 PDF、加密 PDF、损坏文件返回 400，不执行 OCR。Markdown 保留源文本标记，不渲染 HTML。
- 列表 SQL 不加载 LONGTEXT；详情不泄露磁盘路径；前端以后显示正文时应当作文本，不能直接 v-html。
- 上传异常（包括事务提交失败）手动删除文件。数据库事务不会回滚文件系统。
- 删除先锁定归属匹配的记录，将文件移动到同目录随机 .deleting 文件；数据库删除失败则恢复。提交成功后删除暂存文件；原文件缺失只告警并继续删除记录。
- 磁盘清理/恢复失败会记录日志，需要管理员处理。进程崩溃或断电不具有跨数据库和文件系统的绝对原子性：可能留下上传孤儿文件或 .deleting 文件，不能盲删，应与 documents 表核对后恢复或清理。

## 测试

设置上述数据库/JWT环境变量后，在 backend 执行：

```powershell
mvn test
# 无数据库时仅运行独立测试：
mvn "-Dtest=DocumentServiceTest,HealthControllerTest" test
```

集成测试使用真实 MySQL、随机测试用户/知识库、JUnit 临时上传目录，仅清理自己创建的数据；建议日常指定独立测试数据库。Flyway 会对 MYSQL_DATABASE 指定的库应用迁移。

覆盖四种真实文件、解析及持久化、列表/详情、用户隔离、路径参数不匹配、无认证、非法格式/超限、插入/提交失败补偿、删除失败恢复、缺失文件删除、知识库删除关联保护。真实 HTTP 测试验证 multipart 请求，不只依赖 MockMvc。现有认证和知识库测试一并回归；前端执行 npm run build。

## 当前边界

本次验证（2026-09-10）：Java 17.0.18 下 mvn test 共 28 项，失败/错误/跳过均为 0；包含 16 项文档独立测试、6 项文档集成测试和 6 项原有回归测试。npm run build、git diff --check 通过。V3 已应用于 Docker MySQL 的开发数据库。

环境仍有既存告警：本机 Maven 用户级 toolchains.xml 根节点格式不合法、当前 Flyway 提示 MySQL 8.4 高于其已验证支持版本。这次构建、迁移及测试均成功；本阶段未修改全局工具配置或升级原有框架。

同步解析适用于本地作品集 MVP，不是面向不可信公网文件的完整沙箱：尚无病毒扫描、OCR、解析超时/进程隔离、用户磁盘配额及异步队列。列表暂未分页；大规模数据需要后续按需求补充。文本切分及 Embedding 等等待 Stage 5 确认后再开发。
