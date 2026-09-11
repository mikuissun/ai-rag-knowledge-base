# Stage 3：知识库管理与用户数据隔离

## 目标与历史归属

为每个已登录用户提供独立的知识库 CRUD，防止通过猜测知识库 ID 跨用户访问。

对应提交：`868dc8a feat: implement knowledge base management`。本提交也首次加入 JWT 请求拦截器、CurrentUserContext 和 WebMvcConfig；认证完整说明见 [Stage 2](stage2.md)。

## 实现内容

- 新建 knowledge 模块，沿用 Controller → Service → Mapper 分层。
- Flyway `V2__create_knowledge_bases_table.sql` 新建 knowledge_bases。
- 字段包含 id、user_id、name、description、status、created_at、updated_at；user_id 有普通索引。
- 当前使用 MyBatis-Plus 自增 ID 与 LambdaQueryWrapper；列表按 updated_at 倒序。
- 名称必填且最多 100 字符，描述最多 500 字符；保存时去除首尾空白，空描述归一化为 null。
- 初版已有最小 Vue CRUD 页面，Stage 8 将其升级为完整工作区。

## API / 关键类

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | /api/knowledge-bases | 创建当前用户的知识库 |
| GET | /api/knowledge-bases | 当前用户知识库列表 |
| GET | /api/knowledge-bases/{id} | 查询当前用户拥有的知识库 |
| PUT | /api/knowledge-bases/{id} | 修改名称、描述 |
| DELETE | /api/knowledge-bases/{id} | 物理删除知识库 |

创建和更新使用 `{"name":"服务政策","description":"售后相关资料"}`。CreateKnowledgeBaseRequest 与 UpdateKnowledgeBaseRequest 都没有 userId 字段。

KnowledgeBaseResponse 包含 id、name、description、status、createdAt、updatedAt；当前没有文档数量字段。删除成功返回 ApiResponse.ok()，不返回已删除实体。

关键类：

- KnowledgeBaseController：从 CurrentUserContext.requireCurrentUser().id() 获取身份。
- KnowledgeBaseService / KnowledgeBaseServiceImpl：实现 CRUD 和归属校验。
- KnowledgeBaseMapper：继承 BaseMapper。
- KnowledgeBase：映射 knowledge_bases。
- CreateKnowledgeBaseRequest、UpdateKnowledgeBaseRequest、KnowledgeBaseResponse：隔离请求字段与持久化实体。

## 核心流程与 ownership check

```text
Bearer JWT → 身份验证 → CurrentUserContext
           → Controller 取得 userId
           → Service：
                create：用已认证 userId 设置归属
                list：WHERE user_id = 当前用户
                get/update/delete：先查 id + user_id
           → 返回 DTO / 统一异常
```

`findOwnedKnowledgeBase(id, userId)` 查询条件同时包含知识库 id 和当前 userId；查不到时统一返回 404“知识库不存在”。

必须准确区分读取与最终写入：当前 update/delete 先通过上述组合条件取得已归属实体，再调用 MyBatis-Plus 的 updateById/deleteById。不是每一条 UPDATE/DELETE SQL 都包含 user_id。当前 API 不提供所有权转移，客户端也不能修改实体 userId。

Stage 4～7 通过 `getByIdAndUserId` 复用此归属检查，避免各模块重新实现不同的权限规则。

## 安全设计

- 请求中的 userId 不是身份来源，即使客户端额外发送该字段，也不能替换 JWT 中的用户身份。
- 用户 A 的列表不返回用户 B 的知识库。
- 用户 A 查询、修改或删除用户 B 的知识库统一得到 404，不额外暴露对象是否存在。
- DTO 不接受 status、userId 等实体字段的任意覆盖。
- 缺少有效 JWT 返回 401；非法 JSON、空名称、长度越界返回 400。
- 数据库冲突由 GlobalExceptionHandler 映射为 409。

当前存在文档的知识库因 Stage 4 引入的外键约束不能直接删除，返回 409；必须先删除文档。此约束不是 Stage 3 原始迁移中的设计。

## 测试 / 验证

`KnowledgeBaseIntegrationTest` 使用两个随机用户和各自的 Token，覆盖：

- V2 表存在、创建归属正确、列表隔离、本人详情和修改。
- 用户 A 读取、修改、删除用户 B 的知识库均被拒绝。
- 删除本人知识库后记录消失，用户 B 的记录保留。
- 无认证、非法 JSON、空名称、名称和描述过长被拒绝。

准备可用 MySQL 和 JWT_SECRET 后执行：

```powershell
cd backend
mvn "-Dtest=KnowledgeBaseIntegrationTest" test
```

手动验证时，分别注册两个用户，以 A 的 Token 调用 B 的知识库路径；预期 GET/PUT/DELETE 均为 404。不要用真实业务数据测试删除。

本次整理核对了测试源码，没有重新运行数据库测试或虚构历史测试数量。

## 当前边界

- 用户级隔离，不是组织租户、共享知识库或成员角色权限。
- 列表未分页，也不聚合文档数量。
- 不支持恢复已删除知识库、软删除或所有权转移。
- 文档、向量的删除联动属于后续阶段，当前规则见 [Stage 4](stage4.md) 和 [Stage 6](stage6.md)。
