# 项目 Agent 指南

## 项目是什么

本仓库是模拟资产管理系统的代码库。前端为 React 18、TypeScript、Vite、Ant Design
与 styled-components；后端为 Java 21、Spring Boot、Spring JDBC，数据库为
MySQL 兼容模式的 OceanBase。

## 工作如何驱动

开发意图从对话开始，而不是来自长期维护的需求/设计文档（那条线已退役，见文档位置）。
工作经由安装在 `~/.dsh/skills` 的 Matt Pocock 技能集流转（MIT；署名见该目录下的
`LICENSE.mattpocock`）：

- `ask-matt` 把当前处境路由到合适的技能。
- 先用 `grilling` / `grill-with-docs` / `wait-what` 把意图磨清楚。
- `to-spec` 综合出特性规格；`to-tickets` 拆成纵向的 tracer 工单，每张含验收标准与
  阻塞边。每张工单带一行 `测试：`，按 正常 / 边界 / 异常 列出用例。
- 用 `tdd` + `implement` 测试先行实现；每张工单落成一个小而可独立验证的提交。
- 收尾用 `code-review`；难定位的回归用 `diagnosing-bugs`，需要来源支撑的答案用
  `research`，进行中的 git 冲突用 `resolving-merge-conflicts`。

一个新需求沿一条固定序列走。`docs/development-flow.md` 保存完整表格（负责人、产物、
退出标准、各阶段命令）以及热修、缺陷诊断、调研、冲突的快捷路径：

1. 路由并磨清意图（`ask-matt` / `grilling` / `wait-what`）。
2. `to-spec` 把规格发布到 `docs/plans/<yyyy-mm-dd>-<slug>.md`。
3. `to-tickets` 切出带阻塞边的纵向 tracer 工单。
4. 对齐待测的接缝，并按 正常 / 边界 / 异常 列出用例。
5. 测试先行实现，一次一条用例（`tdd` + `implement`）。
6. 跑最小相关验证（见下方「验证」）。
7. `code-review` 双轴评审；重构放在这里，不要塞进红绿循环。
8. 按工单提交（前后端分开），然后归档规格。

产物纪律（作为治理保留）：未配置 issue tracker，因此 `to-spec` / `to-tickets` 发布到
`docs/plans/<yyyy-mm-dd>-<slug>.md`（一个特性一个文件，工单内嵌或旁置拆分）。禁止在
仓库根目录创建 `.scratch/`、工单转储或一次性的进展笔记。调研笔记放 `docs/research/`。

## 按任务查阅

- 领域术语或遗留业务规则：只有当任务触及遗留行为时才查阅归档参考 —— 术语见
  `docs/archive/2026-09-07-doc-driven-development/root/CONTEXT.md`，历史决策见
  `docs/archive/2026-09-07-doc-driven-development/adr/` 下的 ADR。
- 遗留 schema 兼容性：归档的
  `docs/archive/2026-09-07-doc-driven-development/migrations/` 加上后端自身的资源。
- 本地 MySQL 联调：`docs/local-development.md`。
- 其余情况：代码即事实 —— 阅读相关的 `backend/` 包或 `frontend/` 特性目录及相邻实现，
  并遵循你在那里看到的既有分层（controller → application service → domain →
  infrastructure adapter）与命名约定。
- 不要查看 `.docx`、`node_modules`、`dist`、`target`、`.playwright-cli` 或 `output`，
  除非任务明确需要其中的产物。

## 仓库结构

保持仓库根目录干净稳定。根目录是白名单；其他一切都在具名目录里。

根目录允许：`AGENTS.md`、`README.md`、`.agents/`、`.claude/`、`backend/`、`docs/`、
`frontend/`、`scripts/`、`skills-lock.json`，以及标准 dotfiles（`.editorconfig`、
`.env.example`、`.env.local`、`.gitignore`）。

- 禁止在根目录创建缓存或生成产物目录（`.pnpm-store`、`.playwright-cli`、
  `.superpowers`、`.worktrees`、`output/`、`node_modules/`、`dist/`、`target/`）。
  项目级 agent 技能放在 `.agents/skills/` 下并由 `skills-lock.json` 锁定；其他生成
  产物放 `/tmp` 或 `scripts/e2e/.logs/`。
- 提交前运行 `scripts/check_repo_structure.sh`；任何意外的根目录条目都会让它失败。

### 文档位置

活跃文档保持精简：

| 内容 | 位置 |
| --- | --- |
| 本地开发 / 数据库手册 | `docs/local-development.md` |
| 新需求开发流程 | `docs/development-flow.md` |
| 测试策略与验证分层 | `docs/testing-strategy.md` |
| 特性规格与工单（活跃） | `docs/plans/` |
| 调研结论（活跃） | `docs/research/` |
| 已退役的文档驱动体系（冻结历史） | `docs/archive/2026-09-07-doc-driven-development/` |

退役目录保存了旧的 requirement.md 基线及生成的 docx、CONTEXT.md 术语表、模块需求、
技术设计、ADR、迁移、设计规格、R2C 流水线/模板资产（`.ai/`、`.prompt/`）以及 docx
生成脚本。把它当作只读历史；未经人类明确决定，绝不要从中复活任何「事实来源」文档线。

新文档一律放 `docs/` 下。绝不在仓库根目录新增 markdown、docx 或 PDF 文件。

## 硬规则

- 前端命令一律用 `pnpm`。不要创建 `package-lock.json`。
- 嘈杂的 Git、Maven、pnpm、构建、测试、diff 与日志输出用 `rtk` 处理。诊断需要未经
  过滤的失败输出时用 `rtk proxy`。
- 用 `rg` 定位代码，然后只读取最小可用的文件范围。
- 真实数据库是唯一数据源。后端默认 `local` profile，连接所配置的 MySQL/OceanBase
  实例；`src/main` 中没有内存仓储或种子 mock 数据。内存实现只作为测试替身放在
  `src/test`。开发或验证期间绝不连接或变更生产数据库。
- 不要修改遗留主键，不要覆盖遗留源值。
- 成品与产线过滤必须在同一个 `AssetScope` 内匹配；不要把不同 scope 的匹配结果合并。
- 资产生命周期是 `草稿 -> 待整理 -> 已标准化 -> 已停用`。
- 不要提交凭据、本地环境文件、上传数据、生成的浏览器产物或构建输出。
- 保持仓库根目录白名单（见「仓库结构」）；提交前运行
  `scripts/check_repo_structure.sh` 并修掉每一处违规。
- API 错误码只在 `backend/src/main/java/com/tianshu/assets/common/api/ErrorCode.java`
  声明；绝不把响应码写成字符串字面量。`code()` 的唯一性由 `ErrorCodeTest` 断言，
  重复会让 `mvn test` 失败。
- API 错误体只有一种形状（`ApiError`），前端只有一份镜像
  （`frontend/src/types/api.ts` → `ApiErrorBody`）。不要在 service 或 feature 里重新
  声明它。
- **治理域的授权闸门只有一个入口 `GovernanceAuthorizationService`，且是 fail-closed 的**：
  写操作用 `requireGovernanceAdmin(userId, roles)`（要求登录身份**且**具备
  `CONTENT_ADMIN`/`SYSTEM_ADMIN`），读操作用 `requireAuthenticated(userId)`（登录即可，
  数据范围过滤归 S7）。安全默认是「拒」，不是「放行」：
  - 不要新增「不带授权服务」的 controller 构造重载，也不要写
    `if (authorizationService != null)` —— 那会让授权**静默失效**而不是报错（D-002）。
  - 不要把角色判断抄到 controller 里；也不要用只看角色的旧重载（已收为私有）。
  - 治理域**每个 HTTP 处理函数都必须读取 `X-User-Id`**，由
    `GovernanceEndpointIdentityTest` 自动扫描 `governance/api` 包强制 ——
    新增漏读身份头的端点会让 `mvn test` 直接变红（已用变异测试验证会点名到方法）。
  - 改动治理端点后必须跑 `bash scripts/e2e/run-e2e.sh`：其阶段 12 会匿名扫全部治理读写
    端点，任一缺口都会让 E2E 失败并打印泄漏的端点与响应体。
  - 身份头由 `SessionIdentityFilter` 从服务端会话覆写，客户端自报的 `X-User-Id`/`X-User-Roles`
    一律失效；因此不要依赖调用方传来的角色。
- `scripts/db/migrations/` 下的 schema 变更必须先有规格或 `docs/plans/` 记录。破坏性
  变更（删列、改列类型、加 NOT NULL）还要停下来等待人类明确确认。
- 往 `pom.xml`、`package.json` 或锁文件里新增依赖，必须在规格或提交正文里写明理由。
  不要为了省下少量代码而引入依赖。
- 每一次对外调用与外部进程都要声明有界超时和明确的失败路径 —— 参照
  `HttpAiCapabilityClient`（连接/请求超时、错误映射）与
  `LibreOfficeDocumentPreviewConverter`（`waitFor` 超时后 `destroyForcibly`）。写明
  重试策略（fail-fast 也算一种），并像 `GovernanceExecutionService` 那样给变更类调用
  加幂等键。
- 绝不记录或返回密钥、令牌、凭据、文件内容或完整请求体；日志用 SLF4J 带上操作与
  操作者。目前还没有 request/correlation id —— 要加就集中加，不要按模块各加一套。
- 未经人类明确确认，不做全仓库或多模块重构：先发布变更清单（文件、重命名、删除、
  API 变更）并拿到同意再动手。重构不进红绿循环，也不进特性工单。
- 业务规则的测试用例（状态流转、统计口径、`AssetScope` 过滤、编号与结算规则）在实现
  前须由人类确认，模型给出的用例只是草稿。用实现本身的公式重算出来的期望值不算测试。
- **`ai-rag/` 不是本仓库的代码。** 它是一个独立仓库（自带 `.git`、远端、分支、治理与
  三服务架构 `web`/`api`/`rag`），只是作为同级工作区放在根下，ep 的 `.gitignore` 已忽略
  它、结构白名单已放行它。**它可以被直接修改** —— 但改动必须遵循它自己的
  `AGENTS.md` 与 `docs/EXECUTION_RULES.md`：用它的 `ruff.toml` 与测试、按它的提交规范
  提交到它自己的仓，不要把 ep 的规矩套上去。
- **注意一个不可见的坑**：ep 忽略 `ai-rag/`，所以在 ep 里 `git status` 看不到它的未提交
  改动。动过 ai-rag 之后，必须单独 `git -C ai-rag status` 确认，否则会留下没人提交的修改。
- **两仓之间的唯一接口是 HTTP 契约**，契约由 ep 定义（`AiCapabilityClient`）、ai-rag 适配
  （`{base}/rag/chat/stream`、`/rag/documents/ingest`、`/rag/extract`；鉴权 `X-Service-Key`；
  命名空间 `ep-docs`）。不共享代码、不共享数据库。
- **契约的自动化覆盖现状（2026-09-12 核实）**：ai-rag 侧的 `rag/tests/test_capability_contract.py`
  已有 12 个**走真实路由**的契约测试（只 monkeypatch 配置与 LLM，不 mock 契约本身）——
  覆盖鉴权双头、ep camelCase 别名、`/rag/extract` 响应字段、base64 运输、namespace 解析、
  `scopes: []` 语义。**真正的缺口有两处**：① `chat/stream` 在 ep 模式（带 `X-Service-Key`）
  下的 **SSE 事件序**没有端到端断言；② ep 侧没有对"它需要什么"（字段名、事件序）的期望测试。
  改动这两处所涉及的行为时，必须手工跑 `node scripts/e2e/rag-contract-smoke.mjs`。

## Git 提交

- 每个完成且可独立验证的版本，必须在其所需检查通过后提交；不要只把完成的版本留在
  工作区。
- 前后端改动分开提交，即使属于同一个产品版本。契约或文档改动在可独立评审时单独成
  一次提交。
- 提交信息用中文 Conventional Commits，例如 `feat(后端): 实现文档首次发布` 与
  `feat(前端): 实现文档检索工作台`。
- 只暂存属于当前版本与层次的文件。绝不把无关的用户改动或生成产物带进版本提交。
- 未经验证不提交。当一个版本包含多次前后端提交时，在提交正文里记录产品版本号。

## 验证

先跑最小的相关检查。对共享契约、跨模块改动或接近发布的工作扩大验证范围。

```bash
# 仓库结构卫生（先跑；必须通过）
scripts/check_repo_structure.sh

# 前端
cd frontend
rtk pnpm lint
rtk pnpm typecheck
rtk pnpm build   # 仅发布门禁：路由/懒加载/Vite 配置/跨特性/发布

# 单个后端测试类
cd backend
rtk mvn -Dtest=AssetControllerTest test

# 后端全量
cd backend
rtk mvn test

# ep ↔ ai-rag 契约冒烟（需先起 ai-rag 的 rag 服务）
node scripts/e2e/rag-contract-smoke.mjs
```

- 仅前端改动：日常提交门禁是 lint + typecheck。`pnpm build` 只在白名单触发时跑
  （发布门禁）：路由或懒加载入口、Vite/打包器配置、跨特性页面挂载，或发布/验收检查
  点。必须跑 build 时用 `rtk` 包裹，并以退出码和失败摘要判断，不要看完整日志。
- 仅后端改动：先跑直接受影响的测试类；共享 API、仓储、配置或领域改动跑全量。
- **改动 ep↔ai-rag 契约时**（`AiCapabilityClient`、`ai/` 模块、`{base}/rag/*` 的端点、
  payload 字段或 SSE 事件序）：跑 `node scripts/e2e/rag-contract-smoke.mjs`。它默认只读、
  不发入库请求也不调 LLM，可反复跑；改了事件序再加 `--with-llm`。ai-rag 侧虽已有 12 个
  契约测试，但不覆盖 ep 模式的事件序与 ep 侧的期望，这两处只有这个脚本能验。
- **这条脚本不进 pre-commit**：它需要 ai-rag 的 rag 服务在跑（默认 8000），而那个服务在
  提交时未必启动。它是按需验证，不是提交门禁。
- 没有自动化覆盖的 UI 行为：在合适的桌面视口为受影响的工作流提供浏览器证据。
- 不要重复一次未发生变化且已成功的检查。反复失败要先诊断再重跑同一命令。
- 本地提交门禁：`bash scripts/install-hooks.sh` 会从 `scripts/git-hooks/` 安装受版本
  管理的 hook（结构白名单 + 密钥扫描 + 暂存了 `frontend/` 时的前端 lint/typecheck）。
  后端测试故意不放进 hook；请自行运行。只有用 `--no-verify` 才能绕过，并在提交正文里
  说明原因。

## 收尾之前

- 只审查相关 diff，并确认无关的用户改动保持完好。
- 报告改动的文件、跑过的检查，以及任何仍需人类确认的行为。
- 最终报告保持简洁；不要粘贴完整文件、日志或测试输出。
