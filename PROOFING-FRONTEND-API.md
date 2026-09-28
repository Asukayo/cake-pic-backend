# 摄影选单前端接口对接文档

更新日期：2026-09-27。依据当前 `proofing` Controller、DTO、VO 和 Service 源码整理，供前端编写 API 方法和页面。**用户已确认前四阶段实现与验收通过；具体验证用例和运行记录未收录在仓库。**第五阶段的成片上传、交付、ZIP 接口暂缓，本文不把它们列为可调用接口。

## 1. 通用约定

- 以下路径包含应用的 `/api` 前缀。例如 Controller 的 `/proofing/public/items` 对外为 `/api/proofing/public/items`。
- 业务响应统一为 `{ code, data, msg }`；成功时 `code === 0`、`msg === "ok"`。业务异常通常仍返回这一 JSON 包装，前端必须检查 `code`，同时处理非 2xx、网络、上传和跨域错误。
- 后端把 `Long` **响应字段序列化为字符串**。项目、照片、资产、submission ID，项目版本，以及分页的 `current`、`size`、`total`、`pages` 等长整数都按字符串处理；不要用 JavaScript `Number` 保存可能很大的 ID。请求中的 `expectedVersion` 和 ID 可传十进制字符串。`Integer` 字段仍为数字。
- `Date` 类型字段见 `expiresAt`、`expireDate`、`confirmedAt`、`previewUrlExpiresAt`。前端解析具体时间字符串格式时以实际 HTTP 响应为准。
- 员工接口沿用网站登录 Cookie（Spring Session），跨域请求需携带凭证。客户接口使用分享链接换得的 `X-Proofing-Session` 请求头，**不使用员工 Cookie 代替**。客户会话只绑定一个项目，客户接口不接受外部 `projectId`。
- 状态为 `DRAFT → SELECTING → CONFIRMED`；源码还定义 `DELIVERED`、`CLOSED`。第五阶段暂缓，因此当前没有把项目推进到 `DELIVERED` 的接口。关闭可从未关闭状态执行，关闭后客户请求被拒绝。
- 写操作涉及版本时，始终使用最近一次响应的 `version` 或 `currentVersion` 作为下一次请求的 `expectedVersion`。收到 `40901` 后重新读取项目及明细，再让用户确认是否重试。

### 员工权限

后端按项目实际所属空间鉴权，不信任前端传入的空间 ID。私人空间只允许其所有者；团队空间按成员角色检查：

| 权限 | 当前团队角色 | 用途 |
| --- | --- | --- |
| `proofing:view` | viewer、editor、admin | 查看项目、明细、固定清单和预览 |
| `proofing:manage` | editor、admin | 创建、修改草稿、上传/移除预览、发布、分享/撤销 |
| `proofing:close` | admin | 关闭选单 |

前端可调用现有 `GET /api/spaceUser/permissions?spaceId=...` 获取当前用户的 `permissionList` 控制按钮显隐；最终以业务接口的后端权限校验为准。客户不需要登录，也没有上述团队权限，只有有效分享链接及客户会话。

## 2. 员工接口：`/api/proofing/projects`

以下接口均需要员工登录 Cookie。除表中特别注明外，路径中的 `{id}` 是项目 ID。

| 方法与路径 | 输入 | `data` | 权限与状态 |
| --- | --- | --- | --- |
| `POST /api/proofing/projects` | JSON `{ spaceId, title, selectionLimit }` | `Project` | `proofing:manage`；创建 `DRAFT`。标题非空且最多 128 字，选择上限 1～20。 |
| `GET /api/proofing/projects` | 查询 `spaceId` 必填；`page` 默认 1、`pageSize` 默认 10、`status` 可选 | `Page<Project>` | `proofing:view`；`pageSize` 1～50；`status` 只能是已定义状态。 |
| `GET /api/proofing/projects/{id}` | 路径 ID | `Project` | `proofing:view`；仅返回项目元信息，**不含照片或已选数**。 |
| `PATCH /api/proofing/projects/{id}` | JSON `{ expectedVersion, title?, selectionLimit? }`，后两者至少一个 | `Project` | `proofing:manage`；仅 `DRAFT`。 |
| `POST /api/proofing/projects/{id}/previews` | `multipart/form-data`：`file`、`expectedVersion` | `PreviewUpload` | `proofing:manage`；仅 `DRAFT`。单张依次上传，并用响应 `version` 上传下一张。 |
| `GET /api/proofing/projects/{id}/items` | 查询 `page` 默认 1、`pageSize` 默认 10 | `Page<EmployeeItem>` | `proofing:view`；每页最多 50；返回项目全部内部预览明细，没有图片 URL、选择状态和批注。 |
| `POST /api/proofing/projects/{id}/assets/{assetId}/access` | 路径项目 ID、预览资产 ID；无请求体 | `AssetAccess` | `proofing:view`；只签发同项目、仍被 item 引用的 READY PREVIEW；有效 120 秒。 |
| `DELETE /api/proofing/projects/{id}/items/{itemId}` | 查询 `expectedVersion` | 新项目版本（字符串） | `proofing:manage`；仅 `DRAFT`。 |
| `POST /api/proofing/projects/{id}/publish` | JSON `{ expectedVersion }` | `Project` | `proofing:manage`；`DRAFT → SELECTING`；至少一张预览且照片数不少于选择上限。 |
| `POST /api/proofing/projects/{id}/share` | 无请求体 | `Share` | `proofing:manage`；仅 `SELECTING/CONFIRMED/DELIVERED`。每次调用生成新链接并使旧链接、旧客户会话失效；有效期 7 天。 |
| `POST /api/proofing/projects/{id}/share/revoke` | 无请求体 | `true` | `proofing:manage`；删除当前分享，重复撤销仍返回 `true`。 |
| `GET /api/proofing/projects/{id}/submission` | 路径项目 ID | `Submission` | `proofing:view`；只要存在确认记录即可读取固定清单；未确认返回 `40902`。摄影师修图列表应使用此接口。 |
| `POST /api/proofing/projects/{id}/close` | JSON `{ expectedVersion }` | `Project` | `proofing:close`；关闭未关闭的选单，状态变为 `CLOSED`。 |

`PreviewUpload` 来源于本地文件：只支持实际内容为 JPEG/PNG，原图不超过 **20 MiB**、解码后不超过 **4000 万像素**，每个项目最多 300 张预览；服务端缩放到长边最多 1600 像素，生成的 JPEG 预览不超过 2 MiB。全局 multipart 配置目前是单文件 `20MB`、整个请求 `21MB`。上传失败后不要自行递增版本，应重新读取项目。员工预览地址由 `access` 单独获取，列表不直接提供。

## 3. 客户接口：`/api/proofing/public`

除换会话外，所有接口均需请求头 `X-Proofing-Session: <token>`。服务端每次核对会话、当前分享记录和项目状态；链接撤销、轮换、过期或项目关闭后新请求会失败。公开接口响应设置 `Cache-Control: no-store`。

| 方法与路径 | 输入 | `data` | 状态与要点 |
| --- | --- | --- | --- |
| `POST /api/proofing/public/session` | JSON `{ publicId, shareToken }` | `CustomerSession` | 不要求登录。`publicId` 是分享页面路径中的定位符，`shareToken` 来自 URL fragment `#token=...`；只接受 `SELECTING/CONFIRMED/DELIVERED` 且当前有效的分享。会话最长 30 分钟，也不超过分享剩余时间。 |
| `GET /api/proofing/public/project` | 会话头 | `CustomerProject` | 当前项目的状态、选择上限、已选数和版本。确认后已选数来自 submission 快照。 |
| `GET /api/proofing/public/items` | 会话头；查询 `page` 默认 1、`pageSize` 默认 20 | `Page<CustomerItem>` | `pageSize` 1～50。`SELECTING` 返回所有照片及草稿选择/批注；确认后只返回 submission 中的已选照片与固定批注。当前页每张图带短时 `previewUrl`。 |
| `POST /api/proofing/public/assets/{assetId}/access` | 会话头、预览资产 ID；无请求体 | `AssetAccess` | 预览 URL 过期后续签。`SELECTING` 以当前 item 判定可见；确认后以 submission 快照的资产 ID 白名单判定。最长 600 秒，也不超过会话、分享剩余时间。 |
| `PUT /api/proofing/public/items/{itemId}/selection` | 会话头；JSON `{ selected, expectedVersion }` | `SelectionResult` | 仅 `SELECTING`。`selected` 为布尔值；达到选择上限报 `40903`。相同选择状态且版本匹配时不递增版本；取消选择会清空该照片批注。 |
| `PUT /api/proofing/public/items/{itemId}/annotation` | 会话头；JSON `{ annotation, expectedVersion }` | `AnnotationResult` | 仅 `SELECTING` 且照片已选中。`annotation: null` 表示清空；每次成功保存都递增版本，即使内容与原来相同。 |
| `POST /api/proofing/public/confirm` | 会话头；JSON `{ requestId, expectedVersion }` | `ConfirmResult` | 第一次确认要求 `SELECTING`、版本匹配，已选照片至少 1 张且不超过选择上限。`requestId` 由前端为本次确认生成并在超时重试时复用；相同 ID 返回原结果，不同 ID 在已有确认时返回 `40904`。 |
| `GET /api/proofing/public/submission` | 会话头 | `Submission` | 确认结果的固定照片与批注；`SELECTING` 时返回 `40902`。不含预览 URL，需要展示图片时调用 `GET items` 或单张续签。 |

分享链接形如 `https://<前端域名>/proofing/{publicId}#token={shareToken}`。前端读取 fragment 后立即从地址栏移除；明文 `shareToken` 只用于换会话，不写入日志、埋点或长期存储。`CustomerSession.token` 可放内存或 `sessionStorage`；失效后需重新通过有效分享链接换会话。

### 批注坐标

`annotation` 形如 `{ "text": "这里请修掉路人", "rect": { "x": 0.62, "y": 0.25, "w": 0.15, "h": 0.30 } }`；`rect` 可为 `null`。文字去掉首尾空白后须为 1～500 字。矩形坐标相对于**实际预览图像内容**归一化为 0～1：`x,y >= 0`，`w,h > 0`，`x+w <= 1`，`y+h <= 1`。若图片在容器内使用 `object-fit: contain`，先扣除上下或左右留白，再换算矩形；不要按整个容器宽高计算。取消选择会删除草稿批注；确认后只展示固定批注，不能再编辑。

## 4. 前端建议使用的数据类型

下列类型只列页面实际会用到的字段。`DateString` 的线上格式需联调确认；`LongString` 代表后端响应中的十进制字符串。

```ts
type LongString = string;
type DateString = string;
type ApiResult<T> = { code: number; data: T | null; msg: string };
type Page<T> = {
  records: T[];
  current: LongString;
  size: LongString;
  total: LongString;
  pages?: LongString;
};
type ProjectStatus = 'DRAFT' | 'SELECTING' | 'CONFIRMED' | 'DELIVERED' | 'CLOSED';
type Annotation = {
  text: string;
  rect: { x: number; y: number; w: number; h: number } | null;
};
type Project = {
  projectId: LongString; spaceId: LongString; title: string;
  selectionLimit: number; status: ProjectStatus; publicId: string; version: LongString;
};
type PreviewUpload = {
  itemId: LongString; previewAssetId: LongString; displayName: string;
  sortOrder: number; width: number; height: number; version: LongString;
};
type EmployeeItem = {
  itemId: LongString; previewAssetId: LongString;
  displayName: string; sortOrder: number;
};
type Share = {
  createdBy: LongString; projectId: LongString;
  shareUrl: string; expireDate: DateString;
};
type CustomerSession = {
  token: string; projectId: LongString; expiresAt: DateString;
};
type CustomerProject = {
  projectId: LongString; title: string; status: ProjectStatus;
  selectionLimit: number; selectedCount: LongString; version: LongString;
};
type CustomerItem = {
  itemId: LongString; previewAssetId: LongString; displayName: string;
  sortOrder: number; selected: boolean; annotation: Annotation | null;
  previewUrl: string; previewUrlExpiresAt: DateString;
};
type AssetAccess = { url: string; expiresAt: DateString };
type SelectionResult = {
  operateSuccess: boolean; selected: boolean; selectedCount: LongString;
  currentVersion: LongString; limitationLeft: number;
};
type AnnotationResult = { annotation: Annotation | null; currentVersion: LongString };
type ConfirmResult = {
  submissionId: LongString; selectedCount: number; confirmedAt: DateString;
};
type SubmissionItem = {
  itemId: LongString; displayName: string; previewAssetId: LongString;
  previewWidth: number; previewHeight: number; annotation: Annotation | null;
};
type Submission = {
  submissionId: LongString; confirmedAt: DateString; items: SubmissionItem[];
};
```

确认后的 `GET items` 按 submission 的 `items` 数组顺序分页；返回的 `sortOrder` 是数组下标，可能从 0 开始。前端以接口返回顺序展示即可，不要假定草稿和确认后的 `sortOrder` 起点相同。员工 `GET /items` 不是修图任务清单；修图清单用员工 `GET /submission`。

## 5. 推荐调用顺序

1. 员工：创建项目 → 按响应 `version` 逐张上传预览 → 查看明细与预览 → 发布 → 生成分享链接。上传、删除、修改、发布不要并行复用同一个旧版本。
2. 客户：从链接取 `publicId` 与 fragment token → 换客户会话 → 读取项目和第一页照片 → 选择照片、填写批注，每次写入后更新本地版本 → 确认前展示已选照片和批注 → 生成一次 `requestId` 发起确认。
3. 确认请求超时：保留同一个 `requestId` 重试；页面刷新时先读 `/submission` 判断是否已确认，不要盲目生成新的 `requestId` 再确认。
4. 确认后：客户从 `/submission` 查看固定结果，从 `/items` 获取当前页签名预览；员工从 `/{id}/submission` 查看固定修图清单，并按资产 ID 单独获取短时预览地址。

## 6. 错误处理与当前对接注意事项

| `code` | 前端处理建议 |
| --- | --- |
| `0` | 成功，读取 `data`。 |
| `40000` | 参数不合法，检查表单和请求字段。 |
| `40100` | 员工登录失效，重新登录。 |
| `40101` | 权限不足，或客户会话/分享失效；客户应通过有效分享链接重新换会话。 |
| `40400` | 项目、照片或可访问的预览资产不存在。 |
| `40901` | 版本或状态已变化，重新读取后再操作。 |
| `40902` | 当前状态不允许操作，或 submission 尚未生成；按当前项目状态决定页面。 |
| `40903` | 已达到选片上限。 |
| `40904` | 已由另一个 `requestId` 确认；读取 `/submission` 展示既有结果。 |
| `40905` | 确认时有预览资产不可用，提示联系摄影师。 |
| `41001` | 分享链接无效、过期或已撤销。 |
| `50000` / `50001` | 服务端错误或操作失败，提示重试或联系后台。 |

- 全局 `CorsConfig` 已将 `PATCH` 加入允许的方法；跨域行为以实际前端来源和浏览器请求为准。
- `GET /api/proofing/projects/{id}/submission`、`GET /api/proofing/public/submission` 和确认后的快照读取属于已通过用户第四阶段验收的接口。本文仍以当前源码和实际响应为准；若接口行为变化，需同步更新字段与示例。
- 当前没有第五阶段的成片上传、交付包、ZIP 下载接口；前端不要预留调用尚不存在的路径。
