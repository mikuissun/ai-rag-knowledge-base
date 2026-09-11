# Stage 1：工程初始化与基础能力

## 目标与历史归属

建立能够启动、构建和验证的 Spring Boot / Vue 工程，为后续数据库业务与 AI 能力提供基础。

初始化提交：`751a605 feat: initialize project structure`。

本文同时说明用户基础能力在当前代码中的位置，但不把它们记作初始化提交的成果：`User` 实体、Flyway V1、注册登录和 JWT 实际由 `cd8de14`（Stage 2）加入，详见 [Stage 2](stage2.md)。

## 实现内容

- 后端使用 Java 17、Spring Boot 3.5.0、Maven，包名为 `com.mikuissun.knowledgebase`。
- 引入 Spring Web、Validation、Actuator、MyBatis-Plus 3.5.11、MySQL Connector 和 Flyway 依赖。
- 初始化时已预留 MySQL 数据源配置，但 Flyway 默认关闭；Stage 2 加入迁移后默认启用。当前行为以 `application-dev.yml` 为准。
- 前端初始化 Vue 3、TypeScript 和 Vite，提供页面骨架；Element Plus 和完整工作区由 Stage 8 接入。
- Docker Compose 提供 MySQL 与 Qdrant。当前文件使用 MySQL 8.4、Qdrant 1.14.1，默认端口为 3306、6333、6334，数据使用命名卷保存。
- 提供 `.env.example`、`.gitignore`、健康检查和架构/开发计划文档。

基础目录：

```text
backend/
  pom.xml
  src/main/java/com/mikuissun/knowledgebase/
    KnowledgeBaseApplication.java
    common/api/ApiResponse.java
    controller/HealthController.java
  src/main/resources/
    application.yml
    application-dev.yml
  src/test/java/
frontend/
deploy/docker-compose.yml
docs/
.env.example
```

上述是基础结构，不是当前全部目录。当前 auth、user、knowledge、document、vector、search、rag、llm 模块由后续阶段逐步加入。

## 核心流程与 Health API

`Maven → KnowledgeBaseApplication → Spring MVC → HealthController → ApiResponse`。

| 方法 | 路径 | 当前响应 |
| --- | --- | --- |
| GET | /api/health | HTTP 200，`{"code":200,"message":"ok"}` |

`HealthController` 使用 `ApiResponse.ok()`。当前空 data 不输出；统一响应格式曾在 Stage 2 扩展，以上描述的是当前接口。

```powershell
curl.exe http://localhost:8080/api/health
```

该接口不查询 MySQL、Qdrant 或 DashScope，因此 HTTP 200 只表示此应用接口可响应，不证明整条 AI 链路可用。

## User 基础实体与注册登录衔接

以下为 Stage 2 加入、当前可用的用户基础能力：

- `user/entity/User.java` 映射 `users` 表，包含 id、username、password、nickname、email、status、createdAt、updatedAt。
- `UserMapper` 继承 MyBatis-Plus `BaseMapper<User>`。
- `UserServiceImpl.register` 检查用户名、编码密码并保存用户；数据库唯一约束处理重名冲突。
- `AuthService.login` 校验密码和账号状态，返回 Token 与不含密码的用户资料。
- `POST /api/auth/register` 与 `POST /api/auth/login` 由 `AuthController` 提供。

完整认证、安全和验证方法见 [Stage 2](stage2.md)，避免重复描述为 Stage 1 原始实现。

## 配置与安全设计

- 数据源从 MYSQL_HOST、MYSQL_PORT、MYSQL_DATABASE、MYSQL_USERNAME、MYSQL_PASSWORD 读取。
- MySQL 容器初始化密码从 MYSQL_ROOT_PASSWORD 读取；不能把真实值写进配置模板。
- `.env`、构建产物、IDE 文件和日志由 Git 忽略规则排除。
- 当前可通过根目录 `scripts/dev-start.ps1` 注入本地环境并启动基础设施与后端。脚本来自后续 `a2d14d5` 提交，不属于初始化提交。
- 已初始化的数据卷不会因修改 MYSQL_ROOT_PASSWORD 自动重置密码；停止容器时不使用 `down -v`。

## 测试 / 验证

- 当前 `HealthControllerTest.shouldReturnOkForHealthCheck` 使用独立 MockMvc 验证 HTTP 200 和响应字段，不依赖数据库。
- 在 backend 目录执行 `mvn "-Dtest=HealthControllerTest" test`；在 frontend 目录执行 `npm run build`。
- 全量 `mvn test` 包含后续数据库集成测试，需要正确配置可用 MySQL；不能把独立 Health 测试通过视为所有依赖验证通过。

本文件整理依据为 Git 提交、当前源代码和测试源码；本次文档整理未重新执行构建或启动服务。

## 当前边界

Stage 1 是工程底座，未包含文档、Embedding、向量检索或问答业务。用户实体和认证属于后续基础建设，完整当前架构见 [architecture.md](architecture.md)。
