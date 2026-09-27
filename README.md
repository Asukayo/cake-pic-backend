# Sharive｜图库与摄影选片后端

Sharive 是一个基于 Spring Boot 的图片管理后端。原有图库支持公共作品分享、个人与团队空间、图片检索和协作编辑；新增的摄影选片模块让摄影师创建选片单、上传私有预览、邀请客户限额选片并批注，最后固定确认清单。仓库与 Maven artifact 仍使用 `cake-pic-backend` 名称，当前是**单个 Spring Boot 应用、单个 Maven 模块**。

## 当前功能与进度

| 范围 | 当前源码中的能力 | 边界 |
| --- | --- | --- |
| 图库与空间 | 用户登录、公共图库、个人／团队空间、成员角色、图片上传与搜索、空间统计 | 图库仍使用原有 `picture` 数据与存储流程。 |
| 图片处理 | Caffeine + Redis 公共图库分页缓存、批量编辑、AI 扩图、WebSocket + Disruptor 协作广播 | 正式批量编辑接口同步调用 `updateBatchById`；线程池分片方法未接入正式接口。协作会话和编辑状态保存在进程内。 |
| 图库可靠删除 | 图片逻辑删除、空间额度扣减与清理任务／Outbox 同事务落库；Kafka 消费、重试、死信与定时补偿 | 接口成功不代表 COS 文件已经删完；重试耗尽仍需排查或人工处理。 |
| 摄影选片前四阶段 | 项目创建、私有预览上传与发布、分享和客户会话、限额选片与批注、确认快照及员工查看 | **后端代码已写**；尚无 MySQL／Redis／私有 COS／HTTP 和并发场景的完整联调验收。 |
| 成片交付 | `DELIVERED` 状态已在枚举中预留 | 成片上传、ZIP 打包与交付接口尚未实现。 |

选片主流程为 `DRAFT → SELECTING → CONFIRMED`，项目也可关闭为 `CLOSED`。上表区分代码现状与验证结果；详细请求和响应见[选片前端接口文档](PROOFING-FRONTEND-API.md)。

## 技术栈

| 用途 | 技术 |
| --- | --- |
| Web 与数据 | Java 11、Spring Boot 2.6.13、Spring MVC、MyBatis-Plus、MySQL |
| 身份与权限 | Sa-Token、Spring Session、Redis、空间成员角色权限 |
| 缓存与消息 | Caffeine、Redis、Kafka、Outbox |
| 文件与协作 | 腾讯云 COS、Spring WebSocket、LMAX Disruptor |
| 其他 | 阿里云 AI 扩图、Hutool、Knife4j |

## 关键实现

### 摄影选片

- `proofing_project` 保存空间归属、状态、选片上限和版本；`proofing_item` 保存照片选择与批注；`proofing_asset` 管理私有预览文件；`proofing_submission` 保存确认时的照片和批注快照。
- 先在事务外处理图片，再用短事务预留 `STAGING` 资产；COS 上传不占用数据库事务，上传后再用短事务关联照片并转为 `READY`。失败或超时文件由状态条件和定时任务补偿；MySQL 事务不能直接回滚 COS 操作。
- 员工按项目真实所属空间做权限校验。客户用分享令牌换短期会话；Redis 保存令牌摘要及有效期，每次请求重新校验当前分享状态。图片经资产归属检查后签发短时 COS GET URL。分享撤销会阻止新的业务请求，已签出的 URL 仍可能在自身有效期内访问。
- 选片、批注与确认都使用项目行锁和版本校验。确认时在同一 MySQL 事务中写入固定清单并推进状态；建表脚本以 `UNIQUE(projectId)` 约束每单仅有一份确认记录。实际数据库结构及并发行为仍需联调验证。

### 原图库清理与性能

- 图片删除先提交业务状态、额度变动、清理任务和 Outbox 事件；发布器投递 Kafka，消费者按任务状态判重并回收 COS 文件。Kafka 监听容器使用 `ack-mode: record`，记录处理成功后由容器提交 offset，监听方法没有手动 ACK。
- 二级缓存只用于公共且审核通过的图库分页查询，采用 Caffeine → Redis → MySQL 回填；当前写路径未对这组缓存 Key 做完整主动失效，不能承诺强一致。
- `/api/pic/edit/batch` 当前走同步分批更新。仓库另有线程池分片方法与对照测试，但不能把实验路径描述为正式接口的执行方式。

## 代码布局

```text
src/main/java/com/sharkycake/
├── user/             用户与登录
├── space/            空间、成员权限与统计
├── picture/          图库、上传、协作与清理任务
├── proofing/         摄影选片、私有预览、分享、确认快照
├── infrastructure/   COS、Redis、Outbox、持久化与外部 API 适配
└── common/           通用响应、异常与健康检查
```

这些包按业务归属组织；`proofing` 与原图库共用应用和数据库，但预览文件使用独立资产记录与私有 COS 桶配置。

## 本地运行

1. 准备 JDK 11、Maven、MySQL、Redis、Kafka，以及图库和选片使用的 COS 存储桶。选片桶应配置为私有。AI 扩图需要单独配置阿里云密钥。
2. 根据实际数据库状态执行 [`src/main/resources/sql`](src/main/resources/sql) 中所需的建表或变更脚本。仓库没有自动数据库迁移；已有数据库不要不加检查地重复执行建表脚本。
3. 将 [`application-example.yml`](src/main/resources/application-example.yml) 复制为 `src/main/resources/application.yml`，填写数据库、Redis、Kafka、COS、`proofing.shareBaseUrl` 等配置。实际密钥不要提交；`application.yml` 已被 `.gitignore` 忽略。
4. 在 IDE 中运行 `com.sharkycake.CakePicBackendApplication`。当前 `pom.xml` 为 Spring Boot Maven 插件设置了 `skip=true`，因此不要直接把 `mvn spring-boot:run` 当作已验证的启动方式。

服务示例配置使用端口 `8123`、上下文路径 `/api`：

| 入口 | 地址与说明 |
| --- | --- |
| 健康检查 | `GET http://localhost:8123/api/health`；仅表示 HTTP 服务可达，不检查外部依赖。 |
| 接口文档 | `http://localhost:8123/api/doc.html`；Knife4j。 |
| 员工选片接口 | `/api/proofing/projects`；沿用员工登录会话。 |
| 客户选片接口 | `/api/proofing/public`；除换会话外使用 `X-Proofing-Session`。 |
| 图库与空间 | `/api/pic`、`/api/space`、`/api/spaceUser`、`/api/space/analyze`。 |
| 协作 WebSocket | `/api/ws/picture/edit`。 |

## 进一步阅读

- [选片前端接口文档](PROOFING-FRONTEND-API.md)：当前接口、权限、请求和响应字段。
- [选片开发手册](PHOTO-PROOFING-HANDBOOK.md)：分阶段设计与历史记录；其中的阶段快照须以当前源码为准。
- [选片上传与发布流程](PROOFING-STAGE2-FLOW.md)、[分享与并发选片流程](PROOFING-STAGE3-FLOW.md)：局部流程图。
- [Sharive 面试题](SHARIVE-INTERVIEW-QA.md)：按当前源码整理的实现与边界说明。

本项目用于学习和个人作品展示。
