# 全系统 Review 差距清单与切片计划（2026-09-07）

> 依据：三路只读审查（后端差距 / 前端功能 / 前端 UI/UX），对照归档的 V2.0 PRD
> 与 2026-08-15 实施基线。产物落 `docs/plans/`，见根 `AGENTS.md` 流程。

## 0. 待决策项（阻塞对应票，需产品/设计确认，不阻塞日常切片）

| # | 决策 | 涉及 |
|---|---|---|
| D1 | 认证与数据范围（SL-02）：引入真实会话？在此之前敏感写操作默认拒绝还是维持 demo 可写？e2e/演示依赖 demo-user 头 | P0 安全票 S1 |
| D2 | 顶栏深蓝 vs 品牌绿双色系 + Logo 金：既定品牌还是统一 | UI/UX B |
| D3 | 资产详情：抽屉版 AssetDetailDrawer/AssetRelationMap 为孤儿代码，与详情页重复；关系图定位（浏览 vs 导航） | UI/UX B/C、功能 P1-3 |
| D4 | 治理子页是否套共享治理壳（tab/面包屑） | UI/UX B、功能 P1-4 |
| D5 | 文档草稿保存后是否允许页内二次编辑 | UI/UX B-11 |
| D6 | 上传默认归组策略与批量确认呈现 | UI/UX B-10 |
| D7 | 搜索页目录 vs 侧栏双筛选源的"全部资料"语义 | UI/UX B-9 |
| D8 | 资产检索"全部排序规则"的 PRD 口径 | 功能基线过期 |

## 1. 切片 S1 — 授权最小防线（安全，先立票、受 D1 阻塞）

- T1.1 服务端废弃对 `X-User-Roles` 客户端头的信任：敏感写（停用资产/文档、评论治理、标准启停、任务启动）改为服务端解析后的身份。
- T1.2 `SystemAdminController` 写接口强制 SYSTEM_ADMIN；未接认证前默认拒绝。
- T1.3 前端移除硬编码 `governanceIdentity`（governance/api.ts:36-39）与 demo-user 常量，改为显式未登录策略。
- 验收：curl 自报头不再生效；e2e 策略按 D1 结论调整。

## 2. 切片 S2 — 契约与领域正确性（后端为主，推荐先做）

- T2.1 盘点页筛选/分页契约对齐：GovernanceInventoryPage 驼峰参数 ↔ 后端下划线绑定（api.ts:79-86、InventoryController:25-36），统一命名或 @RequestParam 别名 + 契约测试。
- T2.2 治理任务进度接口接通或收敛：任务状态/计划 PATCH 恒拒（LEGACY_READ_ONLY）与前端 `{completed}` 契约错位（TaskController:129-134/152-154、api.ts:220-226）——确认合法迁移语义后实现，否则收敛前端入口。
- T2.3 资产生命周期守卫：`submit()` 仅允许 DRAFT（AssetWriteService:74-104），禁止已停用/已标准化回退；补单测。
- T2.4 治理直写状态加前置校验：`markStandardized` 要求当前"待整理且未停用"（JdbcGovernanceAssetAdapter:94-104）；补单测。
- T2.5 可见口径修正：documentrelation byDocument 按注释语义补用户维度过滤或改注释与测试（AssetDocumentRelationService:105-115）。
- T2.6 健壮性：旧维度/extensionStore 未启用时返回明确错误而非静默空页（OceanBaseAssetRepository:70-99）；saveDraft/@Valid 与分页参数 @Min/@Max；异常文案不外泄、补关键日志（ApiExceptionHandler:162-165）。

## 3. 切片 S3 — 前端低风险 UX/功能修复（独立于决策项）

- T3.1 假可点控件：预览/下载/打包下载/关系图节点/“查看全部 N 项”/帮助——未实现一律 disabled+Tooltip（AssetDetailDrawer:204-244、AssetRelationMap:116-141、AppShell:341）。
- T3.2 上传"清空"加 Popconfirm（UploadPage:881）；"校验中"180ms 假状态改为与真实后端行为一致（497/517、846-854）。
- T3.3 危险/风格统一：window.confirm → Modal.confirm（DocumentCreatePage:100）；hover 过渡统一 ~160ms。
- T3.4 键盘可达：列表行补 keydown(Enter)（AssetSearchPage:822）；Drawer 文本域补 label。
- T3.5 Drawer 宽度 min(880, 100vw-24px)（AssetDetailDrawer:220）。
- T3.6 收藏页分页化（favorites/index.tsx:105-113，当前全量拉取+客户端过滤）。

## 4. 未来切片（单独立项，含决策后）

- S4 功能补齐：资产详情"完整操作记录"区块（后端 OperationLogCriteria+assetId、前端区块）；治理批量认领/分配/移交接通（GovernanceResponsibilityController 已备 PUT/GET）；死代码清理（AssetDetailDrawer/AssetRelationMap/unifiedSearchService 依 D3）。
- S5 UI 令牌化与双筛选源重构（ConfigProvider theme；目录/侧栏统一；依 D2/D7）。
- S6 详情/关系页 IA 重构（依 D3/D4）；搜索页 URL 单一事实源重构。
- S7 认证与数据范围（SL-02，依 D1）：范围过滤下沉到资产/文档/关系/治理查询。

## 5. 基线过期修订（下次写代码前顺手改）

独立关系浏览已实现（relation-browser/）；盘点统计与缺字段页已存在；检索 4 种排序全链路已通 —— 归档的 implementation-baseline.md 相应行已过期，仅存档不追改。

## 6. 实施状态（2026-09-07）

- S2 已完成并提交（后端 2feabad + 前端 e45cdbb）：T2.1 盘点契约对齐（前端 snake_case + 契约测试）；T2.2 收敛（移除无调用方且契约错位的 updateGovernanceProgress；任务 /status 恒拒为有意 LEGACY 只读防护，进度更新走计划进度端点）；T2.3 submit 仅 DRAFT；T2.4 markStandardized 源状态校验；T2.5 可见口径注释+测试；T2.6 盘点分页 @Min/@Max、扩展维度未启用明确报错、停用审计失败日志。
- T2.6 剩余挂起：saveDraft/@Valid 严格化（草稿允许部分字段，语义与提交校验冲突，需产品口径 D9）；IllegalArgumentException 原始文案回显 sanitize 降级为通用文案待评审（现多为业务中文文案）。
- S3（前端低风险 UX）实现中。
- S3 已完成并提交（d5d47e0）：T3.1 假可点控件 disabled+Tooltip；T3.2 清空 Popconfirm + 移除假"校验中"；T3.3 Modal.confirm 统一 + hover 过渡；T3.4 键盘 Enter/aria-label；T3.5 Drawer 宽度自适应；首页工作台错误态+重试。门禁 lint 0/0、typecheck 干净、vitest 5/5。
- T3.6 收藏页：核实后端 GET /api/v1/favorites 无分页契约，保持现状并加 TODO——后端分页单独立票（S4-新增）。
- 待决策/后续：D1 数据范围侧（S7）、D2/D4/D7(S5/S6 设计)、D9(草稿校验口径)、收藏分页后端票；UI 视觉回归需人工浏览器确认。
- S1 匿名写收紧已完成并提交（2026-09-09，后端见下方提交链 + 脚本）：SessionIdentityFilter 对**无会话**写方法（POST/PUT/PATCH/DELETE，`/api/v1/auth/**` 除外）返回 401 `auth_failed` 信封，匿名读保持 demo 兼容（数据范围过滤仍归 S7）；过滤器随 dev/local 会话生效——oceanbase 等尚未接入用户存储的 profile 需在接入后以同等策略启用。前端已随 D1 移除 demo-user 硬编码（T1.3），未登录由路由守卫导向登录页。
  - e2e：`flow.mjs` 自动登录 emp-admin/demo123 携带会话 Cookie 驱动全流程（62/62 通过）；curl 实测匿名 POST 401、匿名 GET 200。
- S3-补（待做，UX 一致性）：检索/筛选"读请求进行态"——列表视图绑 loading=isFetching（AssetSearchPage:818）而图集仅首载骨架、搜索按钮无 loading/防连点（:687）；文档检索与系统管理同类。目标：任一读请求有进行态、图/表视图反馈一致、防连点。来源：UI/UX 审查（原越权代理 a88bb77b 的 IA-2.6 已核实后收编，其 layout 方案文档未采纳并已移出仓库）。
- R1a 治理轨道首屏已完成并提交（4e7c936）：GovernanceRail/StatCards/StepPanel/RailHome + governanceRailModel（20 单测）；查看 http://127.0.0.1:5173/sys/drawing「治理总览」。R1b（分派/移交动作接通）与 R2（我的待办，依赖 D1）待做。
- S3-补 检索分页与进行态标准化已提交（c1c9fe0，PaginationBar 统一 + isFetching 反馈）。
- 存量类型债（独立票）：frontend typecheck 红 ≈17 处——测试/mock/详情组件未随 types/asset.ts 更新（AssetRelation 缺 createdBy 等、DictionaryItem 缺字段、home CLOSED 比较、uploads SegmentedOptions 等）；assetService 缺失导入已修（6704eab）。另注意：仓库存在其它并行工作线（如游离提交 55ef30a），提交前核对 git log/status 再精确暂存。
