# 第二阶段：私有预览与发布流程图

> 依据当前 proofing 后端源码绘制。图表示已写代码的流程，不代表数据库和 COS 联调已通过。

```mermaid
flowchart TD
    A["员工上传预览图"] --> B["事务外：校验、修正朝向、重编码 JPEG"]
    B --> C["事务 1：锁 project 行<br/>校验权限、DRAFT、expectedVersion、300 张上限<br/>插入 PREVIEW / STAGING，设置 1 小时过期<br/>项目版本不变"]
    C --> D["事务外：上传唯一 objectKey 到私有 COS"]

    D -->|COS 明确成功| E["事务 2：再次锁 project 行<br/>重查权限、DRAFT、版本、STAGING 未过期"]
    E -->|通过| F["同一事务：插入 item<br/>asset 变 READY，项目版本 +1"]
    E -->|关联失败| G["短事务：条件标记 STAGING → DELETE_PENDING"]
    D -->|COS 异常或结果不明| H["保留 STAGING，不立即删除"]
    H --> I["定时任务：过期 STAGING<br/>条件标记为 DELETE_PENDING"]
    G --> P["DELETE_PENDING：可清理"]
    I --> P

    F --> L["员工分页查 item<br/>从 project 反查空间权限"]
    L --> S["按 assetId 申请访问<br/>校验项目权限、READY、item 引用"]
    S --> U["签发最多 120 秒的 GET URL<br/>前端显示私有预览"]

    F --> R["仅 DRAFT 可移除<br/>锁 project、校验 expectedVersion<br/>删除 item，asset 标记待删，版本 +1"]
    R --> P
    F --> N["发布：锁 project、校验 DRAFT 和版本<br/>图片数至少 1 且不少于 selectionLimit<br/>状态变 SELECTING，版本 +1"]

    P --> X["事务外：删除 COS 对象"]
    X -->|成功| Y["按状态条件删除 asset 行"]
    X -->|失败| P
```

**复习时记住三点：**

1. 两次上传事务之间是 COS 网络调用，不能用一个数据库事务包住；失败文件靠 `STAGING → DELETE_PENDING` 补偿。
2. 上传完成、草稿移除和发布都先锁同一条项目行，再检查 `DRAFT` 与 `expectedVersion`。发布后，迟到的上传完成和图片移除会被拒绝。
3. 图片列表返回 `itemId`、`previewAssetId` 等展示字段，不返回 COS 地址；签名访问另行校验权限、资产归属、`READY` 和 item 引用。已有签名 URL 在到期前仍可能有效。

对应源码：[上传编排](src/main/java/com/sharkycake/proofing/upload/ProofingPreviewUploadService.java)、[上传短事务](src/main/java/com/sharkycake/proofing/upload/ProofingPreviewTxService.java)、[清理任务](src/main/java/com/sharkycake/proofing/cleanup/ProofingAssetCleanupService.java)、[项目移除与发布](src/main/java/com/sharkycake/proofing/service/impl/ProofingProjectServiceImpl.java)。

真实数据库、私有 COS、并发与失败补偿的验证仍待完成。
