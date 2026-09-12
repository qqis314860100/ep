# 新需求开发流程（本仓库适用）

从「一句话需求」到「已提交、可独立验证的版本」的固定路径。

**结论先说**：主干与业界标准一致（spec → 竖切 ticket → TDD → 评审 → 门禁 → 提交），
本仓库在此基础上更严；已知缺口是**没有 CI** 与**没有强制第二人评审**，见 §5。

配套阅读：`AGENTS.md`（硬规则、验证阶梯、提交纪律）、
`docs/testing-strategy.md`（四层验证设施、最小验证集决策表）、
`docs/plans/README.md`（spec/ticket 产物约定）、`docs/local-development.md`（本地库）、
`scripts/e2e/README.md`（端到端脚本）。

## 1. 八个阶段

| # | 阶段 | 谁主导 | 产物 / 落点 | 退出条件 |
| --- | --- | --- | --- | --- |
| 1 | 路由与拷问 | 人 + `ask-matt` / `grill-me` / `wait-what` | 对话结论（不落文件） | 需求能用一句话说清；边界场景与「范围外」明确；方案被拷问过一轮 |
| 2 | 成 spec | `to-spec` | `docs/plans/<yyyy-mm-dd>-<slug>.md`：问题、方案、验收标准、范围外 | 每条验收标准可判定真假（不是「体验良好」这类描述） |
| 3 | 拆票 | `to-tickets` | 同文件内或并列 tickets 目录：每票 `Blocked by` / `Status` / 验收勾选项 / `测试：` 行 | 每票是端到端可独立验收的**竖切**；阻塞边无环；单票工作量以「一次提交」为限 |
| 4 | 对齐 seam + 用例清单 | 人审 | ticket 的 `测试：` 行按 **正常 / 边界 / 异常** 三类列举 | seam 已与人对齐（未确认的边界不写测试）；业务规则用例已人工核验 |
| 5 | 竖切实现 | `tdd` + `implement` | 代码 + 测试 | 一条用例 → 一次最小实现 → 红转绿 → 下一条；不做横向铺测试，不做顺手重构 |
| 6 | 本地验证 | 人 / agent | 命令输出与（UI 行为）浏览器证据 | 见 `docs/testing-strategy.md` §3 决策表：结构检查 → 相关测试类 → 契约/仓储改动跑全量 |
| 7 | 评审 | `code-review`（Standards + Spec 双轴） | 评审结论 + 修正提交 | 两个轴均无阻塞项；重构与测试改名在此阶段完成 |
| 8 | 提交与归档 | 人 | 提交（信息用中文 Conventional Commits） | 见 §2 提交契约；spec 完成后移入 `docs/archive/<yyyy-mm-dd>-<里程碑>/` |

## 2. 阶段 6–8 的提交契约

- 提交前依次确认：`scripts/check_repo_structure.sh` PASS → 最小验证集通过 → 无残留脚手架/生成物。
- 本地提交门禁已提供：`bash scripts/install-hooks.sh` 把版本化的 hook（源在 `scripts/git-hooks/`）软链进
  `.git/hooks/`，每次提交自动跑 结构白名单 + 密钥扫描（gitleaks 或内置规则）+ 暂存了 `frontend/` 时的
  oxlint/tsc。**后端测试故意不在 hook 内**（mvn 太慢），仍需按上面的阶梯自行跑。绕过用
  `git commit --no-verify`，并在提交说明里写明原因。
- **后端与前端改动分开提交**，即使属于同一产品版本；契约与文档改动在可独立评审时单独提交。
- 只暂存属于当前版本与当前层的文件；不得夹带他人未完成的改动或生成物。
- 提交正文在「一个版本包含多个前后端提交」时记录产品版本号。
- 一个 ticket 完成即提交，不要留在工作区攒批。

## 3. 阶段 5–6 会自动挂上的既有硬约束

这些规则已在 `AGENTS.md` 的 Hard Rules 中，动手前先确认不会撞上：

| 触碰面 | 约束 |
| --- | --- |
| API 错误码 | 只在 `backend/src/main/java/com/tianshu/assets/common/api/ErrorCode.java` 声明；不得写字面量。`ErrorCodeTest` 断言 `code()` 唯一，重复即 `mvn test` 失败 |
| 错误响应形状 | 后端唯一形状 `ApiError`，前端唯一镜像 `frontend/src/types/api.ts` → `ApiErrorBody`；禁止在 service/feature 里重复声明 |
| 数据库迁移（`scripts/db/migrations/`） | 先有 spec 或 `docs/plans/` 记录；破坏性变更（删列、改列类型、加 NOT NULL）必须停下来等人工确认 |
| 新增依赖（`pom.xml` / `package.json` / lockfile） | 必须在 spec 或提交正文写明理由；不得为了少写几行代码而引入依赖 |
| 数据来源 | 真库是唯一数据源；`src/main` 禁内存实现/种子数据/运行时 mock；验证期不得连生产库 |
| 领域不变量 | `AssetScope` 同域过滤不跨 scope 拼匹配；资产生命周期 `草稿 → 待整理 → 已标准化 → 已停用`；legacy 主键与源值不可改 |

## 4. 什么时候不走全流程

| 场景 | 走法 |
| --- | --- |
| 单行修复、文案、样式 | 直接改 + `pnpm lint`/`typecheck`（或对应测试类）+ 一次提交；结构检查仍要跑 |
| 联调/线上故障、回归 | `diagnosing-bugs`（先复现再定位，不在同一轮里顺手重构） |
| 需要外部事实支撑 | `research` → `docs/research/` |
| 正在进行的合并/变基冲突 | `resolving-merge-conflicts` |
| 结构性改进（模块边界、接口形状） | 先 `codebase-design` / `improve-codebase-architecture` 定 seam，再回到阶段 2 |
| 纯探针性验证（方案是否可行） | `prototype`，结论回写 spec，原型不进主干 |

免流程的阈值只有一条：**改动不改变对外可见行为、不触碰契约/迁移/依赖**。

## 5. 与业界标准的对照

**一致的（这部分就是标准做法）**

- spec → 竖切 ticket → TDD → 评审 → 门禁 → 提交，等同 XP / Shape Up 的 spec + 小步增量 + Conventional Commits。
- 契约单一来源（错误码注册表 + 唯一性测试、单一错误体 + 前端镜像）符合「契约集中、不重复声明」的通行实践。
- 迁移需 spec、破坏性变更人工签核，对应数据库变更治理的 expand/contract 思路。
- 依赖需理由，直接对抗「AI 顺手加依赖」这类典型漂移。

**比标准更严（少见但合理）**

- 需求先被拷问（`grilling`）再成 spec。
- 根目录白名单 + `scripts/check_repo_structure.sh` 作为提交门禁。
- 真库唯一数据源、`src/main` 禁内存实现与 mock。
- 显式规定「不要重复跑未变更的成功检查」，把验证成本当作一等约束。
- 文档范式退役与归档纪律（见 `AGENTS.md` → Document Locations）。

**缺口（真实差距）**

1. **没有 CI**。远端是 GitHub，但根白名单不允许 `.github`，远端没有任何强制门禁。
   本地已由 pre-commit 兜底（`bash scripts/install-hooks.sh`：结构白名单 + 密钥扫描 +
   暂存 `frontend/` 时的 lint/typecheck），但它只在**本机**生效。补 CI 需两步：把 `.github`
   加入 `scripts/check_repo_structure.sh` 白名单，建最小 workflow（结构检查 + `mvn test` +
   `pnpm lint`/`typecheck`）。
2. **没有强制的第二人评审**。所有提交直接落 `main`；`code-review` 是双轴并行子代理的**自评**，
   强度高于「AI 自检」，但不等于他人评审。多人协作时需要 PR 流程。
3. 次要：tickets 无 tracker，`Status:` / 阻塞边靠人维护；单人 + agent 模式够用，多人并行会腐化。

在缺口补齐之前，§1 阶段 6–8 的人工确认点不能省——它们就是当前的兜底。

## 6. 通用清单对照（避坑）

通用「Vibe Coding 最佳策略」类清单（面向绿地 MES / Agent 项目）有三处与本仓库冲突，
照做会撞门禁：

| 通用清单建议 | 本仓库的替代做法 |
| --- | --- |
| 根目录维护 `PROJECT_SPEC.md`，每次对话喂给 AI | 根目录 `*.md` 会被 `scripts/check_repo_structure.sh` 判违规，且「文档即唯一可信源」已于 2026-09-07 退役。用 `AGENTS.md`（自动加载）+ `docs/plans/<日期>-<slug>.md` 承载 spec 与 ticket |
| 让 AI 生成 DDL，人评审主键 / 索引 / 分表 | legacy 兼容系统，schema 多为既成事实：不许改 legacy 主键、不许覆盖 legacy 源值，AI 只**核对**既有 schema；`scripts/db/migrations/` 改动先有 spec，破坏性变更等人工确认 |
| 先铺完 ER/DDL → 领域层 → CRUD 的前置瀑布 | 与竖切 tracer ticket 相悖：`to-tickets` 出端到端可独立验收的竖切票（先例：`docs/plans/2026-09-09-ai-assistant-phase1-tickets/`） |

清单里值得吸收的四条已写入 `AGENTS.md` Hard Rules：对外调用的超时与失败路径、
日志与错误响应不含密钥、无人工确认不得大范围重构、业务规则用例人工核验。
清单没覆盖但更致命的不变量（真库唯一数据源、`AssetScope` 同域、生命周期状态、
legacy 主键、提交与结构纪律）同样在 Hard Rules 里。
