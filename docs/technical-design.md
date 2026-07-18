# 仿真数模资产管理系统技术设计

## 1. 技术基线

- 前端：React 18、TypeScript、Vite、Ant Design 5、styled-components。
- 后端：Java 21、Spring Boot 3、Bean Validation、Spring JDBC。
- 数据库：OceanBase MySQL 兼容模式。
- API：REST，统一使用 `/api/v1` 前缀。
- 测试：Vitest、Testing Library、JUnit 5、Spring MVC Test。

## 2. 架构原则

- 业务领域与基础设施隔离，控制器不直接访问数据库。
- 默认开发 profile 使用内存仓储，避免开发过程连接线上库。
- OceanBase profile 通过环境变量连接，首个版本只读兼容旧 `sys_drawing`。
- 新结构采用扩展表和关系表，不改变旧主键，不覆盖旧原始值。
- V1.5 扩展 DDL 位于 `docs/migrations/V1_5__asset_extension_schema.sql`，盘点/核对查询位于 `docs/migrations/V1_5__legacy_reconciliation_queries.sql`；两者仅作为受控迁移输入，不由应用启动自动执行。
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
- `POST /api/v1/governance/tasks`：创建治理任务，初始状态为进行中。
- `GET /api/v1/equipment-interconnections`：按设备编码、基地和拉线查询设备互联数据。
- `GET /api/v1/uploads/mine`：按当前用户查询本人上传资产，可按状态筛选。
- `POST /api/v1/uploads/files`：上传单个文件，返回临时对象键、大小和 SHA-256 摘要。
- `GET /api/v1/assets/{assetId}/files/{fileId}?preview=true`：经权限校验后以内联方式预览可预览文件。
- `GET /api/v1/assets/{assetId}/files/{fileId}`：经权限校验后下载文件。

集合响应包含 `data` 和 `meta`；错误响应包含 `error.code`、`error.message` 和可选字段错误详情。

## 7. OceanBase 约束

- 基于旧系统 MySQL 5.7 结构，默认采用 OceanBase MySQL 模式。
- 正式开发前，以线上只读结构为准确认列名和数据类型。
- `sys_drawing.id` 继续作为稳定资产标识。
- 首个 OceanBase 适配器只读旧字段，不执行建表、回填或更新。
- `ASSET_EXTENSION_SCHEMA_ENABLED` 默认关闭；开启前必须完成 V1.5 扩展表和迁移核对，适配器才读取扩展字段，旧字段仍作为回退来源。
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

## 10. 旧数据扩展策略

- 旧 `sys_drawing` 及收藏、评论、点赞、日志表继续作为历史事实来源，原主键和原字段不修改。
- 新增资产包、文件归组、适用范围、治理任务、映射和关系能力时采用扩展表，通过旧 `drawing_id` 建立一对一或一对多关联。
- 迁移程序先生成盘点报告，再执行可重复的增量迁移；每个批次记录源数量、成功数量、异常数量和校验结果。
- 旧平台、旧拉线和旧分类只作为原始文本保留和搜索，不自动推断基地、标准拉线或工序段。
- 收藏、评论、点赞和操作日志继续关联稳定资产 ID；孤立记录进入异常清单，不通过删除数据解决。
- 正式切换采用“结构扩展 -> 只读核对 -> 小范围双读 -> 全量切换”的顺序，并准备只切回旧读链路的回退方案。
- 扩展表首先承接平台范围、模块标签、模块超链接、设备互联、文件清单和审计事件；旧收藏、评论、点赞和操作日志先保持原表读取，迁移报告逐项核对后再切换。

## 11. 工程质量基线

- 前端路由按业务页面懒加载，React、Ant Design 和查询库作为独立长期缓存包。
- 后端单元测试覆盖搜索规则和异常分支，接口测试覆盖分页响应、参数校验和统一错误结构。
- 搜索中的产品和生产维度必须在同一个 `AssetScope` 中命中，禁止跨适用范围拼接。
- `AssetScope` 同时保留兼容用 `platform`、标准 `platformFamily` 和 `platformVariant`；八个平台子类通过平台族与子类组合筛选，历史数据缺失子类时不自动猜测。
- 模组相关属性使用受控 `moduleTags`、`standardEquipmentModule`、`linkedModuleAssetIds` 和 `equipmentInterconnectCode`，模块超链接只引用资产 ID，不复制文件。
- 数据写入阶段使用乐观锁防止后台治理并发覆盖；冲突返回明确的 `409 Conflict`。
- 文件上传阶段校验扩展名、实际类型、大小、内容摘要和安全扫描结果，文件存储地址不直接暴露给前端。
- OceanBase schema 变更使用版本化迁移脚本并在非生产环境验证；当前只读切片不包含任何线上 DDL 或 DML。
