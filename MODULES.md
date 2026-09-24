# 模块结构（2026-09-24）

本项目目前仍是单个 Maven 模块、单个 Spring Boot 应用和一套数据库。Java 包按业务归属整理，便于定位代码与逐步收紧依赖；包名本身不提供微服务隔离。

| 包 | 负责的能力 | 主要子包 |
| --- | --- | --- |
| `user` | 用户、登录、平台角色权限 | `controller`、`service`、`mapper`、`entity`、`auth` |
| `space` | 空间、成员、空间权限、统计 | `controller`、`service`、`mapper`、`entity`、`auth` |
| `picture` | 图片、上传、审核、协作编辑、清理任务和消费者 | `controller`、`service`、`mapper`、`upload`、`websocket`、`cleanup` |
| `infrastructure` | COS、Redis、MyBatis 配置、Outbox、外部 API | `cos`、`cache`、`persistence`、`outbox`、`api` |
| `common` | 通用请求、响应、异常与健康检查 | `controller`、`exception` |

每个业务包内再按职责放 Controller、Service、Mapper、实体及 DTO。跨业务包优先通过对方的 Service 接口协作，不直接访问对方 Mapper。图片删除通过 `SpaceService.decreaseUsage` 扣减空间额度；删除图片、额度扣减、创建清理任务与写 Outbox 仍位于同一 MySQL 事务。

Mapper 接口位于各业务包及 `infrastructure.outbox.mapper`，由 `MyBatisPlusConfig` 显式扫描。XML 继续放在 `src/main/resources/mapper`，其 namespace 和 resultMap 类型已同步更新。Knife4j 显式扫描四处 Controller 包。根包 `com.sharkycake` 保留启动类，Spring 的组件扫描继续覆盖所有子包。

若以后拆成独立服务，需要先确定数据归属、跨服务调用方式和事务处理策略。当前空间权限、图片及用户仍有直接的实体和服务依赖；图片删除依赖的本地数据库事务也不能在拆分后原样跨库使用。WebSocket 的会话与占用状态保存在当前进程内，拆实例前还需要解决共享状态。不要仅靠移动 Maven 模块或改包名宣称完成微服务化。

RedisTemplate 与 Spring Session 使用 `GenericJackson2JsonRedisSerializer`。旧 Redis 值可能保存了迁移前的 Java 类名，升级后读取旧登录会话或对象缓存可能反序列化失败；部署切换时应安排用户重新登录，并按实际缓存键与 TTL 处理旧数据。尚未连接 Redis 验证兼容性。

验证记录：重构前后 `mvn -o -DskipTests clean compile` 均成功。新增的 `MapperXmlTest` 通过 JUnit 平台执行，1 项成功、0 项失败；实际解析了 6 个 Mapper XML 并绑定到迁移后的接口。当前 Maven 默认使用的 Surefire 3.2.5 缺少本地 JUnit 平台组件，直接运行 `mvn test` 会在测试启动前失败；该单项测试改用已缓存的 JUnit 平台依赖执行。其余集成测试尚未运行，尤其 `BatchEditTest` 和 `removeAllTestPic` 会修改或删除数据库数据。尚未启动应用或连接 MySQL、Redis、Kafka、COS 验证实际业务链路。
