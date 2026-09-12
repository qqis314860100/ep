# Vibe Coding 清单 → 本仓库映射

通用「Vibe Coding 最佳策略」类清单（面向绿地 MES / Agent 项目）与本仓库治理的对照。
目的：**别再拿通用清单来套**——哪些已经有了、哪些直接冲突、哪些是你们真正该守而它没写的。

配套阅读：`AGENTS.md`（硬规则与流程）、`docs/development-flow.md`（八阶段流程）、
`docs/testing-strategy.md`（四层验证）、`docs/plans/README.md`（spec/ticket 约定）。

> 放置说明：本文是长期参考（convention），不放在 `docs/plans/`（那里是 feature spec 与 ticket，
> 有"完成即归档"的生命周期）。

## 1. 结论

通用清单的**主干**与业界标准一致，本仓库已经把其中"对的部分"做成了自动化门禁 + 技能流程：

| 通用清单主张 | 本仓库现状 |
| --- | --- |
| 分层约束（别把业务写进 Controller） | 已是事实：`com/tianshu/assets/<module>/{api,application,domain,infrastructure}`，`AGENTS.md` 明文规定调用方向 |
| 小任务、短上下文、一次几百行 | 更强：竖切 tracer ticket + 单票一次提交（`docs/plans/README.md`） |
| 测试先行、用例清单 | 已有四层验证设施（`docs/testing-strategy.md`），并有 68 个后端测试类 / 40 个前端测试文件 |
| 代码评审不能全交 AI | `code-review` 技能：Standards + Spec 双轴并行子代理，强于"让 AI 自检" |
| 验收标准可判定 | ticket 的验收勾选项 + `测试：` 行 |

## 2. 直接冲突或不适用的三条（照做会撞门禁）

| 通用清单建议 | 为什么不能照做 | 替代做法 |
| --- | --- | --- |
| 根目录维护 `PROJECT_SPEC.md`，每次对话喂给 AI | 根目录 `*.md` 会被 `scripts/check_repo_structure.sh` 判违规；且"文档作为唯一可信源"这条线已于 2026-09-07 退役并归档 | `AGENTS.md`（自动加载的宪法）+ `docs/plans/<日期>-<slug>.md`（本 feature 的 spec 与 ticket），不额外喂文档 |
| 让 AI 生成 DDL，人评审主键/索引/分表 | 本仓库是 legacy 兼容系统：schema 多为既成事实，不许改 legacy 主键、不许覆盖 legacy 源值 | AI 只**核对**既有 schema；`scripts/db/migrations/` 改动先有 spec，破坏性变更等人工确认 |
| 先铺完 ER/DDL → 领域层 → CRUD 的前置瀑布 | 与竖切 tracer ticket 相悖，且既有代码不适合按层横切 | `to-tickets` 出端到端可独立验收的竖切票（先例：`docs/plans/2026-09-09-ai-assistant-phase1-tickets/`） |

## 3. 已吸收进 `AGENTS.md` Hard Rules 的四条

| # | 规则 | 为什么在本仓库特别重要 |
| --- | --- | --- |
| 1 | 对外调用声明超时与失败路径；变更类调用要有幂等键；重试策略必须写明（"不重试"也是策略） | 出网点目前只有 `HttpAiCapabilityClient`（已做 connect/request 超时与 `TIMEOUT`/`AUTH_FAILED`/通信失败映射），新链路最容易漏；幂等先例见 `GovernanceExecutionService` 的 `idempotencyKey` |
| 2 | 日志与错误响应不得包含密钥、令牌、文件内容、完整请求体；加日志用 SLF4J 带操作、主体与关联 id | 本仓库发生过凭据落到明文环境与历史记录的事件；且现有日志覆盖极低（仅 2 个类用 SLF4J），新增日志等于定标准 |
| 3 | 禁止无人工确认的大范围重构：先出变更清单（文件、重命名、删除、API 变更）再动手；重构不塞进红绿循环与 feature ticket | AI 最爱的"顺手重写"在 legacy 兼容系统里代价最高 |
| 4 | 业务规则用例（状态流转、统计口径、`AssetScope`、编号/结算）先经人核验再实现；复刻实现公式的期望值不算测试 | AI 会编造"很合理但不存在"的用例，且 tautological 断言永远绿 |

## 4. 通用清单没写、但你们最该守的五条

| # | 不变量 | 自检问题 | 落地位置 |
| --- | --- | --- | --- |
| 1 | `AssetScope` 作用域一致性：产品/生产线过滤必须同域，禁止跨 scope 拼匹配 | 这个查询的每个过滤条件出自同一个 scope 吗？ | `AGENTS.md` Hard Rules |
| 2 | 资产生命周期 `草稿 → 待整理 → 已标准化 → 已停用`，不得自造中间态 | 我引入的状态在生命周期里存在吗？迁移是否只走既有路径？ | `AGENTS.md` Hard Rules + 领域测试 |
| 3 | 真库是唯一数据源：`src/main` 禁内存实现/种子数据/运行时 mock；验证期不连生产库 | 我加的实现是不是只在测试里合法？ | `AGENTS.md` Hard Rules + `docs/testing-strategy.md` §4 |
| 4 | legacy 主键与源值不可改 | 我的写入会覆盖 legacy 字段或改写主键吗？ | `AGENTS.md` Hard Rules + 归档 migrations |
| 5 | 提交与结构纪律：前后端分开提交、结构检查先跑、根目录白名单、pre-commit 门禁 | `scripts/check_repo_structure.sh` 过了吗？hook 过了吗？ | `AGENTS.md` Git Commits + `scripts/git-hooks/pre-commit` |

## 5. 现成资产索引（先查这里，再考虑引入新规则）

| 需求 | 看哪里 |
| --- | --- |
| 硬规则、验证阶梯、提交纪律 | `AGENTS.md` |
| 新需求八阶段流程与免流程阈值 | `docs/development-flow.md` |
| 测试分层、最小验证集决策表 | `docs/testing-strategy.md` |
| spec / ticket 产物约定 | `docs/plans/README.md` |
| 本地库与联调 | `docs/local-development.md` |
| 端到端闭环脚本 | `scripts/e2e/README.md` |
| 本地提交门禁 | `scripts/git-hooks/pre-commit`、`scripts/install-hooks.sh` |
