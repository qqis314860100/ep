# 仿真数模资产管理系统技术设计

## 1. 技术基线

- 前端：React 18、TypeScript、Vite、Ant Design 5、styled-components。
- 后端：Java 21、Spring Boot 3、Bean Validation、Spring JDBC。
- 数据库：OceanBase MySQL 兼容模式。
- API：REST，统一使用 `/api/v1` 前缀。
- 验证：前端使用 Oxlint、TypeScript 类型检查、Vite 构建和浏览器验收；后端使用 JUnit 5、Spring MVC Test。

## 2. 架构原则

- 业务领域与基础设施隔离，控制器不直接访问数据库。
- 默认开发 profile 使用内存仓储，避免开发过程连接线上库。
- 本地联调使用 `local` profile，连接 `localhost:3306/tianshu`；凭据从环境变量读取，禁止写入受版本控制文件。
- OceanBase profile 通过环境变量连接，首个版本只读兼容旧 `sys_drawing`。
- 新结构采用扩展表和关系表，不改变旧主键，不覆盖旧原始值。
- V1.5 扩展 DDL 位于 `docs/migrations/V1_5__asset_extension_schema.sql`，盘点/核对查询位于 `docs/migrations/V1_5__legacy_reconciliation_queries.sql`；两者仅作为受控迁移输入，不由应用启动自动执行。
- V1.7 字段治理闭环 DDL 位于 `docs/migrations/V1_7__governance_field_closure.sql`，只新增治理问题、任务、计划、治理项、结果、确认、验收、作业和审计扩展表，不修改旧表主键或来源字段。
- API 使用资源名词、标准 HTTP 状态码和统一错误结构。
- 前端页面按业务功能组织，不按 Ant Design 组件类型组织。

## 3. 后端分层

```text
controller -> application service -> domain repository -> infrastructure adapter
```

- `asset.domain`：数模资产、适用范围、资产关系和领域枚举。
- `asset.application`：查询用例、分页和业务校验。
- `asset.infrastructure`：内存仓储和 OceanBase 读取适配器。
- `common.api`：响应结构、错误处理和跨域配置。
- `governance`：控制器只编排鉴权和 DTO；任务、执行、确认、验收、正式应用与审计应用服务依赖各自仓储端口，内存和 JDBC 适配器实现端口。

## 4. 前端页面结构

```text
pages/
├── main/
│   ├── search/       # 搜索首页、搜索入口、筛选和结果
│   └── detail/       # 资产详情、文件预览、评论和关系
└── sys/
    ├── drawing/      # 图纸/数模资产管理与治理
    └── file/         # 文件上传与文件管理
```

搜索首页是默认入口；系统管理功能通过独立导航进入，不与日常资料检索混在同一侧栏层级。

## 5. 首个开发切片

首个切片验证以下闭环：

1. 资产搜索和筛选。
2. 资产列表分页。
3. 资产详情。
4. 资产关系展示。
5. OceanBase 旧表只读兼容接口。

批量上传、治理写入和数据库扩展在该读链路稳定后实施。

## 6. API 契约

- `GET /api/v1/assets`：分页搜索资产。
- `GET /api/v1/assets/{id}`：查询资产详情。
- `GET /api/v1/assets/{id}/relations`：查询资产关系。
- `GET /api/v1/favorites`：查询当前用户收藏的资产。
- `POST/DELETE /api/v1/assets/{id}/favorite`：幂等添加或取消当前用户收藏。
- `GET /api/v1/governance/tasks`：查询治理任务及进度。
- `POST /api/v1/governance/tasks`：创建治理任务，初始状态为草稿。
- `GET /api/v1/governance/issues`：查询字段问题池；任务只接受开放问题 ID 建单。
- `GET /api/v1/governance/tasks/{taskId}/confirmation-rounds/current`、`PUT /api/v1/governance/confirmation-rounds/{roundId}/items/{itemId}/decision`、`POST /api/v1/governance/tasks/{taskId}/confirmation-rounds/{roundId}/complete`：逐项保存并完成业务确认轮次。
- `GET /api/v1/governance/tasks/{taskId}/acceptance-rounds/current`、`PUT /api/v1/governance/acceptance-rounds/{roundId}/samples/{itemId}`、`POST /api/v1/governance/tasks/{taskId}/acceptance-rounds/{roundId}/complete`：读取固定指标与抽样、保存抽样决定并完成验收。
- 任务首次进入待验收状态时，查询当前验收轮次会按启动时冻结的质量策略和已确认治理项事实幂等创建指标与固定抽样；后续查询只读取已固化轮次。
- `GET /api/v1/governance/jobs/{jobId}` 和 `POST /api/v1/governance/jobs/{jobId}/retry`：查询正式应用逐项结果并重试失败项。
- `POST /api/v1/governance/tasks/{taskId}/plans`：仅允许草稿任务新增计划项。
- `PATCH /api/v1/governance/tasks/{taskId}/status`：计划完整后将草稿任务启动为进行中并锁定计划结构。
- `PATCH /api/v1/governance/tasks/{taskId}/plans/{planId}`：仅允许进行中任务更新计划执行状态。
- `PATCH /api/v1/governance/tasks/{taskId}/progress`：仅允许进行中任务更新完成量。
- `GET /api/v1/equipment-interconnections`：按设备编码、基地和拉线查询设备互联数据。
- `GET /api/v1/uploads/mine`：按当前用户查询本人上传资产，可按状态筛选。
- `POST /api/v1/uploads/files`：上传单个文件，返回临时对象键、大小和 SHA-256 摘要。
- `GET /api/v1/assets/{assetId}/files/{fileId}?preview=true`：经权限校验后以内联方式预览可预览文件。
- `GET /api/v1/assets/{assetId}/files/{fileId}`：经权限校验后下载文件。
- `GET /api/v1/assets/{assetId}/comments`：查询评论，并返回当前用户点赞状态和删除权限。
- `POST /api/v1/assets/{assetId}/comments`：以 JSON 发布文字评论，或以 multipart 发布文字和最多 6 张评论图片。
- `POST/DELETE /api/v1/assets/{assetId}/comments/{commentId}/like`：幂等点赞或取消点赞。
- `DELETE /api/v1/assets/{assetId}/comments/{commentId}`：作者或具备评论治理角色的管理员软删除评论。
- `GET /api/v1/assets/{assetId}/comments/images/{storageKey}`：校验图片确实关联该资产评论后返回图片内容。
- `GET /api/v1/dictionaries/categories`：查询固定字典分类及产品、生产层级定义。
- `GET /api/v1/dictionaries/items`：按分类、上级、状态或关键词查询字典项。
- `POST /api/v1/dictionaries/items`：新增受控字典项。
- `PATCH /api/v1/dictionaries/items/{id}`：按版本更新、排序、启用或停用字典项。
- `POST /api/v1/dictionaries/items/{id}/merge`：将源字典项合并到同分类启用目标并保留历史记录。

集合响应包含 `data` 和 `meta`；错误响应包含 `error.code`、`error.message` 和可选字段错误详情。

## 7. OceanBase 约束

- 基于旧系统 MySQL 5.7 结构，默认采用 OceanBase MySQL 模式。
- 正式开发前，以线上只读结构为准确认列名和数据类型。
- `sys_drawing.id` 继续作为稳定资产标识。
- 首个 OceanBase 适配器只读旧字段，不执行建表、回填或更新。
- `ASSET_EXTENSION_SCHEMA_ENABLED` 默认关闭；开启前必须完成 V1.5 扩展表和迁移核对，适配器才读取扩展字段，旧字段仍作为回退来源。
- `local` profile 默认开启扩展结构和数据库写入；资产、范围、文件清单、收藏、评论、点赞与设备互联均读写本地 MySQL。
- 字段治理在默认 `dev` profile 使用内存仓储完成全闭环；`local` profile 通过 V1.7 扩展表持久化；`oceanbase` profile 默认拒绝治理写入，未完成受控迁移和开关验证前不得启用。
- 本地建表脚本为 `docs/migrations/local/V1_5__local_bootstrap.sql`，开发数据脚本为 `docs/migrations/local/V1_5__local_seed.sql`；两者均为幂等脚本，不删除已有数据。
- `dictionary_item` 统一保存产品体系、生产体系、资产分类和关系类型字典；`parent_id` 表达层级，`status` 与 `merge_target_id` 保留停用和合并历史，`version` 防止并发覆盖。
- 所有连接信息由环境变量提供。

## 8. 编码规范

- TypeScript 开启严格模式，不使用 `any` 绕过类型检查。
- React 使用函数组件；业务状态与展示组件分离。
- Java 使用不可变 record 作为请求和响应对象。
- 使用构造器注入，不使用字段注入。
- 输入在系统边界验证，领域异常映射为明确 HTTP 状态码。
- 公开 API、状态枚举和关键业务规则必须有测试覆盖。
- 日志不得记录文件内容、密码、令牌和数据库凭证。

## 9. 文件处理与访问

- 上传接口只接受文件内容，不向浏览器返回永久存储地址。
- 扩展名、大小、已知文件签名和可执行扩展名在服务端校验；上传完成后生成 SHA-256 摘要。
- 当前开发 profile 使用内存对象存储验证闭环；OceanBase profile 明确拒绝写入，生产环境替换为对象存储适配器。
- 下载和预览均通过资产权限校验后的后端接口，响应使用 `no-store`，不暴露对象存储凭证。
- 评论图片沿用文件存储抽象，限制格式、文件签名、单张大小和数量；评论删除后图片入口不可继续访问。

## 10. 旧数据扩展策略

- 旧 `sys_drawing` 及收藏、评论、点赞、日志表继续作为历史事实来源，原主键和原字段不修改。
- 新增资产包、文件归组、适用范围、治理任务、映射和关系能力时采用扩展表，通过旧 `drawing_id` 建立一对一或一对多关联。
- 迁移程序先生成盘点报告，再执行可重复的增量迁移；每个批次记录源数量、成功数量、异常数量和校验结果。
- 旧平台、旧拉线和旧分类只作为原始文本保留和搜索，不自动推断基地、标准拉线或工序段。
- 收藏、评论、点赞和操作日志继续关联稳定资产 ID；孤立记录进入异常清单，不通过删除数据解决。
- 正式切换采用“结构扩展 -> 只读核对 -> 小范围双读 -> 全量切换”的顺序，并准备只切回旧读链路的回退方案。
- 字段正式应用只写资产扩展值并使用结果版本与资产版本保证幂等；验收通过前不得写入。后续映射治理、文件拆分合并和复杂治理动作不属于当前字段闭环切片。
- 扩展表首先承接平台范围、模块标签、模块超链接、设备互联、文件清单和审计事件；旧收藏、评论、点赞和操作日志先保持原表读取，迁移报告逐项核对后再切换。
- 当前开发 profile 的评论与点赞状态用于验证接口和交互；生产切换时必须将同一契约适配到既有 `sys_drawing_comment`、`sys_drawing_comment_like` 和 `comment_img` 字段，完成用户工号映射和对象存储适配后才能启用写入。
- `local` profile 已使用既有评论、点赞和收藏表持久化协作数据；开发文件内容保存到被 Git 忽略的 `.data/files`，数据库仅保存文件键和摘要。

## 11. 工程质量基线

- 前端路由按业务页面懒加载，React、Ant Design 和查询库作为独立长期缓存包。
- 后端单元测试覆盖搜索规则和异常分支，接口测试覆盖分页响应、参数校验和统一错误结构。
- 搜索中的产品和生产维度必须在同一个 `AssetScope` 中命中，禁止跨适用范围拼接。
- `AssetScope` 同时保留兼容用 `platform`、标准 `platformFamily` 和 `platformVariant`；八个平台子类通过平台族与子类组合筛选，历史数据缺失子类时不自动猜测。
- 业务统一使用“蓝本”表示 H03、P02 等可复用标准方案；为兼容既有数据，API 字段 `productLine`、数据库列 `product_line` 和字典分类编码 `PRODUCT_LINE` 暂不重命名，界面及需求文档不得继续称其为“产品线”。
- 模组相关属性使用受控 `moduleTags`、`standardEquipmentModule`、`linkedModuleAssetIds` 和 `equipmentInterconnectCode`，模块超链接只引用资产 ID，不复制文件。
- 字典编码在分类内唯一；父子分类由应用服务校验，包含启用子项的父项不能停用或合并，字典写入必须校验乐观锁版本。
- 资产检索和上传表单从字典 API 读取启用的资产类型、专业类别、文件角色、模组标签、产品体系和生产体系；产品与生产父子项分别联动，不使用前端硬编码选项。
- 数据写入阶段使用乐观锁防止后台治理并发覆盖；冲突返回明确的 `409 Conflict`。
- 文件上传阶段校验扩展名、实际类型、大小、内容摘要和安全扫描结果，文件存储地址不直接暴露给前端。
- OceanBase schema 变更使用版本化迁移脚本并在非生产环境验证；当前只读切片不包含任何线上 DDL 或 DML。
