# Stage 2：用户认证与 JWT 身份上下文

## 目标与历史归属

实现用户注册、密码校验、JWT 生成与验证，并说明当前受保护 API 的认证流程。

- `cd8de14 feat: implement user authentication foundation`：加入用户表、User、BCrypt、认证 API 和 JwtTokenService。
- `868dc8a feat: implement knowledge base management`：加入 JwtAuthenticationInterceptor、CurrentUserContext、CurrentUser 和 WebMvcConfig，将 JWT 接入受保护请求。
- `3aca7ae feat: implement rag chat with citations and sse`：补充异步请求开始时的 ThreadLocal 清理。

因此本文按认证主题组织，但拦截器和 SSE 清理不属于 Stage 2 原始提交。

## 实现内容与关键类

| 类 / 文件 | 职责 |
| --- | --- |
| AuthController / AuthService | 注册登录接口与认证编排 |
| User / UserMapper / UserServiceImpl | 用户实体、MyBatis-Plus 查询和注册持久化 |
| PasswordConfig | BCryptPasswordEncoder |
| JwtProperties / JwtConfig | app.jwt 配置绑定 |
| JwtTokenService | JJWT 0.12.6 签发、验签和过期校验 |
| JwtAuthenticationInterceptor | 解析 Authorization，认证成功后设置身份 |
| CurrentUser / CurrentUserContext | 保存当前请求的 userId、username |
| WebMvcConfig | 注册受保护路径拦截规则 |
| GlobalExceptionHandler / ApiResponse | HTTP 状态与统一错误响应 |
| V1__create_users_table.sql | users 表及用户名唯一约束 |

只使用 Spring Security 的 crypto 模块进行密码编码，当前认证链不是 Spring Security FilterChain。

## API 与注册登录流程

| 方法 | 路径 | 请求 | 成功响应 data |
| --- | --- | --- | --- |
| POST | /api/auth/register | username、password；可选 nickname、email | id、username、nickname、email |
| POST | /api/auth/login | username、password | token、user |

注册流程：

1. Bean Validation 检查用户名为 3～50 位字母、数字或下划线，密码长度为 6～72 个字符；昵称最多 50 字符，邮箱最多 100 字符且符合邮箱格式。
2. 查询用户名是否存在，重复时返回 409。
3. 使用 BCrypt 编码密码，以 status=1 保存用户。数据库唯一约束也防止并发插入重名用户。
4. 返回 UserProfileResponse，不包含密码或密码哈希；注册本身不返回 JWT。

登录流程：

1. 查询用户并执行 PasswordEncoder.matches。
2. 用户不存在或密码错误统一返回 401，账号被禁用返回 403。
3. 签发带 subject=username、userId claim、issuedAt、expiration 的 JWT。
4. 返回 AuthResponse，包含 Token 和用户资料。

JWT_SECRET 从环境变量读取，源码没有默认密钥；签名密钥实际按 UTF-8 字节数检查，至少 32 字节。JWT_EXPIRATION_SECONDS 默认 86400 秒。密钥缺失属于服务配置错误，不应与客户端 Token 过期混淆。

## Bearer Token 与请求认证

```http
GET /api/knowledge-bases
Authorization: Bearer <登录返回的JWT>
```

```text
请求 → WebMvcConfig 匹配受保护路径
     → JwtAuthenticationInterceptor.preHandle
     → 检查 Bearer 前缀
     → JwtTokenService.parseAndValidate
     → 验签、有效期、userId 和 subject 检查
     → CurrentUserContext.set
     → Controller requireCurrentUser().id()
     → Service 归属校验
```

WebMvcConfig 当前保护 `/api/knowledge-bases` 和 `/api/knowledge-bases/**`，因此包括文档、process、index、search、chat 与 chat/stream。注册、登录、Health 不经过该拦截器。

缺少 Authorization、不是源码要求的 `Bearer ` 前缀、Token 无效或过期时返回 HTTP 401。CurrentUserContext 中无用户而调用 requireCurrentUser，也抛出 401 BusinessException。

## ThreadLocal 清理与安全设计

- 同步请求结束时在 `afterCompletion` 调用 `CurrentUserContext.clear()`，内部使用 ThreadLocal.remove()。
- SSE 进入异步处理时，在 `afterConcurrentHandlingStarted` 清理原请求线程身份，防止线程池复用造成残留。
- SSE 异步任务使用 Controller 已获取并显式传递的 userId，不从异步线程重新读取 ThreadLocal。
- 前端 Token 只用于传递身份凭据；服务端必须验证签名，不能信任客户端传入的 userId。
- 所有者权限由 Service 实施，JWT 验证成功不代表可以访问任意知识库。

## 测试 / 验证

- AuthIntegrationTest：users 迁移、注册、密码哈希、重名冲突、登录、无效/过期 Token、非法参数及禁用用户。
- KnowledgeBaseIntegrationTest：受保护 API 的无认证拒绝和多用户隔离。
- Stage7IntegrationTest：普通与 SSE 问答、归属校验和失败路径；不能据此声称存在专门的 ThreadLocal 泄漏压力测试。

准备可用 MySQL 和环境变量后：

```powershell
cd backend
mvn "-Dtest=AuthIntegrationTest,KnowledgeBaseIntegrationTest" test
```

AuthIntegrationTest 自行注入随机 JWT Secret；KnowledgeBaseIntegrationTest 使用应用配置，需要提供 JWT_SECRET。集成测试使用随机测试用户并清理自己的数据，建议使用独立测试数据库。本次只整理文档，未重新运行这些测试。

## 当前边界

- 没有 Refresh Token、Token 黑名单、服务端登出撤销和 RBAC。
- 登出清理的是前端 Token；已经签发的 JWT 在过期前仍可能有效。
- 拦截器不在每次请求中重新查询账号状态；禁用账号的登录校验不等于立即撤销既有 Token。
- 当前只有注册/登录基础校验，没有验证码、密码找回或登录限流机制。
