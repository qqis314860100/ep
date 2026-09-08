# 真实数据对接与 Office 文档上传格式扩展（方案与任务）

> 日期：2026-09-08　状态：已确认（D1 全量补齐 / D2 资产+文档中心 / D3 补充更丰富种子）　关联页面：上传页 / 资料检索 / 资产详情 / 文档中心
> 目标用户诉求（原话拆解）：①连接数据库、对接真实数据；②“目前没有图纸模型”；③增加上传文档格式。

## 1. 背景与现状（均已核实，2026-09-08）

| 项 | 现状 |
| --- | --- |
| 后端运行形态 | `127.0.0.1:8080` 正在运行，工作目录 `backend/`，实为 **`dev`（内存）profile**：资产、字典、文档、治理、协作、文件存储全部走内存仓储，重启即丢。`.env.local`（local profile 指向 `127.0.0.1:3306/tianshu`）**未在运行时生效**。 |
| 本机 MySQL | `127.0.0.1:3306` MySQL 9.6.0 在线，`tianshu` 库已建 16 张表：资产侧（`sys_drawing`、`asset_package_ext/scope_ext/file_ext/audit_ext`、字典 `dictionary_item` 48 条）与 `governance_task/plan` 有数据；**无任何文档表**（`knowledge_document/document_version/document_file` 等均不存在），**无治理扩展表**（`governance_issue/scan_run/data_standard/mapping_rule` 等）。 |
| 真实资产数据 | `sys_drawing` + 扩展共 5 条（id 101~105）：焊接工位总成数模、定位工装数模、输送模块布置数模（三维/混合）、XM-PL01 设备图、PACK 段设备接口图（二维 PDF 图纸），含基地/拉线/蓝本/工序范围、8 条文件清单；种子与 `dev` 内存种子镜像一致。 |
| 迁移脚本 | `docs/archive/.../migrations/` 正本为 OceanBase 风格：`CREATE TABLE IF NOT EXISTS` + **`ALTER TABLE … ADD COLUMN IF NOT EXISTS`（MySQL 9.6 不支持该子句，已实测报错）**；`migrations/local/` 下 launcher 为 `SOURCE docs/migrations/….sql` 相对路径，当前仓库布局（归档后）无法解析。即：**现有脚本不能在当前本机 MySQL 上直接补跑**，需 MySQL 兼容的幂等执行方式。 |
| 上传与预览链路 | 前端上传页 `accept` 与 `recognizedFormats` 已含 `.doc/.docx` 等；后端 `AssetFileUploadController` 做扩展名+签名校验（PDF/PNG/JPEG/DOCX），500MB 上限；预览走 `GET …/files/{id}?preview=true`，`previewable && 转换器支持` 时经 LibreOffice（本机 `/opt/homebrew/bin/soffice` 已安装）转 PDF 内联展示，否则降级。**LibreOffice 转换器当前仅支持 `DOCX/DOC`**；资产文件 `PREVIEWABLE_FORMATS` 与前端预览分支也只认 PDF/图片/DOC(DOCX)。 |
| 默认 dev profile | `application.yml` 默认 `dev`（内存）；`local/oceanbase` 才启用 JDBC。硬规则要求**不改默认 dev profile**。 |

## 2. 目标

1. 真实数据对接：在**不改默认 dev profile 的前提下**，提供可复现的 local（本机 MySQL）启动方式；补齐本机库到后端 `local` profile 所需的最小 schema；联调验证图纸/数模资产（101~105）在各读页面真实可见、可打开，上传/治理/文档读接口不再因缺表 500。
2. 澄清并修复“没有图纸模型”观感：以真实库 5 条资产为准做可见性验收；发现任何页面在真实形态下为空/报错即按缺陷修复。
3. 上传文档格式扩展（资产侧为主）：支持 Office 全套 `.doc/.docx/.xls/.xlsx/.ppt/.pptx` 及 `.csv/.txt`——前端可选/识别/默认角色、后端签名与扩展名校验、可预览标记、LibreOffice 转 PDF 在线预览、下载，全链路一致。

## 3. 非目标

- 不改数据库主键/历史来源值；不删、不清空 `tianshu` 现有数据；不连接或改动生产库。
- 不改 `dev` 默认 profile（不把内存后端替换成 JDBC 默认）。
- 不做文件断点续传、Office 在线编辑、图纸在线标注。
- 不为治理/文档页面**伪造**业务数据（空表即空列表，仅保证不 500）。

## 4. 决策记录（2026-09-08 用户确认）

- **D1 库结构补齐范围 = 资产 + 文档 + 治理全量补齐**：新增 MySQL-9 兼容的幂等补齐脚本，补齐 `tianshu` 缺失的文档表/列与治理扩展表/列；只做 additive，不清库、不删数据；目标：切到 `local` 后资产/字典/文档/治理读接口均不 500。
- **D2 文档格式支持落点 = 资产侧 + 文档中心**：Office 六类（`.doc/.docx/.xls/.xlsx/.ppt/.pptx`）与 `.csv/.txt` 在数模资产上传/预览/下载与知识文档新建/新版本两侧统一放开。
- **D3 验收数据 = 补充更丰富的真库种子**：在 `tianshu` 增补贴近业务的图纸/模型资产种子（含可预览文件落盘），作为“真实数据可见性”与端到端验收基座。

## 5. 方案要点

### 5.1 真实数据（local）启动与库补齐

- 新增 MySQL-9 兼容、可重复执行的“补齐脚本”，**不改写归档正本**：以 `information_schema` 守卫做 `ADD COLUMN`（列不存在才加），`CREATE TABLE IF NOT EXISTS` 保留；按依赖顺序执行（V1_6 文档 → V1_7 治理闭环 → V1_8 标准中心 → V1_9 映射规则 → V1_10 扫描 → V1_11 文档范围与关联 → V1_12 问题时间戳 → V1_13 文档协作）。
- 脚本存放与执行方式按仓库规范落地（不在根目录；不提交凭据/数据）。
- 启动与验证：按 `docs/local-development.md` §3 启动 local profile（可另起端口避免占用现有 8080），健康检查 + 只读冒烟。
- 验收：`information_schema` 含目标表与列；资产 5 条与字典 48 条计数不变；文档/治理/字典/检索读接口在 local 下均 200。

### 5.2 上传文档格式扩展（资产侧与文档中心统一）

- **前端**：上传页（`frontend/src/features/upload/UploadPage.tsx`）与文档中心新建/新版本（`features/documents`，实现时核对 accept 所在组件）的 `accept`、`recognizedFormats`、`defaultRole`、格式摘要同步扩展 `.xls/.xlsx/.ppt/.pptx/.csv/.txt`；预览组件把新格式纳入“转 PDF 后 iframe 预览”分支与图标映射（资产 `DrawingGallery`、文档 `DocumentPreview`/`DocumentFileList`）。
- **后端**（`AssetFileUploadController`）：
  - 签名校验扩展：`.docx/.xlsx/.pptx` 走 ZIP 且按内部条目分型（`word/document.xml`、`xl/workbook.xml`、`ppt/presentation.xml`）；`.doc/.xls/.ppt` 走 OLE 头 `D0 CF 11 E0 A1 B1 1A E1`；`.csv/.txt` 仅做 UTF-8/可打印与大小校验，不做魔数强校验。
  - `defaultRole`：Office/CSV/TXT 默认归「其他附件」（用户可在角色下拉改为「说明附件」等）；不新增字典角色。
  - `PREVIEWABLE_FORMATS` 增加 `DOC/XLS/XLSX/PPT/PPTX/CSV/TXT`。
- **预览**（`LibreOfficeDocumentPreviewConverter` + 资产/文档控制器已具备的 `preview=true` 分流）：`SUPPORTED_FORMATS` 扩展并**按格式写正确临时扩展名**（现逻辑 DOC→`.doc`、其余一律 `.docx`，需修正）；资产与知识文档的写入路径（文件入库时 `previewable` 判定）两侧同步；转换失败保持降级（“暂不支持在线预览”，可下载），不抛错。
- **文档中心侧配套**（D2）：知识文档新建/新版本的文件上传与预览同样放开六类 Office + csv/txt（含文件角色/可预览标记与预览组件），实现时核对文档侧校验入口与现有测试。

## 6. 任务拆解（垂直切片）

### T1　真实数据形态：库补齐 + local 启动冒烟（阻塞后续验证）
- 产出：MySQL-9 兼容补齐脚本（幂等、additive，覆盖资产/文档/治理缺表与列）+ 执行记录 + local 启动冒烟结果。
- 验收：
  1. `information_schema` 中目标表/列齐备；现有 `sys_drawing` 5 条、`dictionary_item` 48 条等计数不变。
  2. local profile 实例：`/actuator/health` UP；`GET /api/v1/assets`、`/documents`、治理读接口（文档与治理范围，见 D1）均 200 不 500。
  3. `dev` 默认 profile 未被改动；未提交凭据/环境文件/上传数据。
- 决策：D1（已确认全量）。

### T2　真库补充更丰富的图纸/模型种子（D3）
- 产出：additive 种子脚本（数据 + 物理文件落盘到 local 文件存储目录）。
- 内容建议（实现时按字典可用范围对齐）：在现有 5 条基础上扩展至 30~50 条贴近业务的数据——覆盖乘用车/商用车多基地与拉线、不同蓝本；类型覆盖三维源模型(X_T/STEP/STP)、二维图纸(PDF/DWG)、混合资产与若干标准设备模块；含可预览主文件（小型 PDF/图片真实落盘）以便图集与在线预览可用；含模块关联与所属部门/责任人。
- 验收：local 形态下图集/检索能翻出新增资产，可预览文件可打开；种子脚本可重复执行不产生重复（以编号/名称幂等判断）。
- 阻塞：T1。

### T3　真实数据可见性验收与修复（“图纸模型”观感）
- 产出：local 形态下关键读页面（首页/资料检索列表+图集、资产详情文件工作台、我的上传等）对种子数据的验证记录；缺陷修复提交。
- 验收：每份资产可搜出、可打开详情；PDF/图片文件可在线预览；二维图纸、三维模型、混合资产类型与基地/拉线筛选结果与数据一致；任意页面空/错位均记录并修复。
- 阻塞：T1、T2（T2 完成前可先用现有 5 条验收）。

### T4　后端：上传格式与预览能力扩展（资产侧 + 文档中心）
- 改动面：`AssetFileUploadController`（资产）与知识文档写入/校验入口（文档，实现时定位 `DocumentController` 对应逻辑）；`LibreOfficeDocumentPreviewConverter`；两侧 `previewable` 判定；单测 `AssetFileUploadControllerTest`、`LibreOfficeDocumentPreviewConverterTest`、文档相关测试。
- 验收：
  1. 六类 Office + csv/txt 在资产上传与文档中心新建/新版本均可上传；签名不匹配（如把 PDF 改名 .xlsx）拒绝并报明确中文错误；可执行后缀仍拦截。
  2. 上传产物 `previewable` 正确；`preview=true` 时 DOC/DOCX/XLS/XLSX/PPT/PPTX/CSV/TXT 经 LibreOffice 转 PDF 返回（本机 soffice 已具备）；缺 soffice/转换失败降级为原文件/可下载提示，不 500。
  3. 相关测试类通过（先跑受影响类，再跑全量）。
- 决策：D2（资产 + 文档中心）。

### T5　前端：上传与预览 UI 扩展（资产侧 + 文档中心）
- 改动面：`UploadPage.tsx`；文档中心新建/新版本上传组件；资产 `DrawingGallery` 与文档 `DocumentPreview`/`DocumentFileList` 等预览/图标映射；相关测试与类型。
- 验收：新格式两侧可选入、正确识别与默认角色、未知后缀提示逻辑不回退；上传后详情可预览 Office 文档（内联 PDF）并下载；组件测试通过；`pnpm lint`/`typecheck` 绿。
- 阻塞：T4 契约确定后可与 T4 并行。

### T6　端到端验收（local 形态）
- 验收：真实样例 `.doc/.docx/.xls/.xlsx/.ppt/.pptx/.csv/.txt` 走「上传 → 资产详情/文档详情 → 在线预览 → 下载」全链路；异常样本（改名/超 500MB/可执行）拦截提示正确；提供桌面视口浏览器证据。
- 阻塞：T1/T4/T5。

### T7　收尾
- 按 AGENTS.md 提交：前端/后端/文档（含种子与执行说明）分层独立提交；`check_repo_structure.sh` 通过；总结仍需人工确认的行为。

## 7. 验证清单（AGENTS.md 要求的最小集合）

```bash
scripts/check_repo_structure.sh
# 后端改动：先跑受影响测试类，共享契约改动跑全量
cd backend && rtk mvn -Dtest=AssetFileUploadControllerTest,LibreOfficeDocumentPreviewConverterTest test
cd frontend && rtk pnpm lint && rtk pnpm typecheck
# UI 行为无自动化覆盖：浏览器证据（桌面视口）
```

## 8. 风险与注意

- 迁移脚本与 MySQL 9.6 语法不兼容是本轮最大执行风险：只按“补齐缺失对象”执行，任何 ALTER 前先查 `information_schema`；**绝不在本地库上做破坏性重建**。
- 现有 8080 为用户进程：验证另起端口实例，不擅自 kill 用户进程。
- Office 转 PDF 有 30s 超时与临时目录，超大文件预览会降级，属预期。
- 前端仍存在记录在案的“存量类型债”（frontend typecheck ≈17 处），T4 只触碰本版本相关文件，不扩线清理。

## 9. 提交规划

1. `docs(计划): 真实数据对接与 Office 上传格式扩展方案`（本文档）
2. 后端版本提交（含补齐/种子脚本引用、T4 后端改动）
3. 前端版本提交（T5）
4. 契约/文档类独立提交（若含迁移执行说明、`docs/local-development.md` 更新）
