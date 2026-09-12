# 测试策略（本仓库适用版）

面向「新增需求」的可执行测试策略：先说清仓库现有的四层验证设施，再给出每个需求从
ticket 到提交的固定动作。

配套阅读：根目录 `AGENTS.md`（验证阶梯与提交纪律）、`tdd` 技能（红绿循环与测试反模式）、
`docs/plans/README.md`（spec/ticket 产物）、`docs/local-development.md`（本地库）、
`scripts/e2e/README.md`（端到端脚本）。

## 0. 通用四步在本仓库的落地情况

通用清单说的「① 生成用例清单 → ② 写业务代码 → ③ 生成单测跑通 → ④ 补充集成测试脚本」，
本仓库具备 **3.5 / 4**，但有三处必须按本仓库的形态修正。

| 通用四步 | 本仓库等价物 | 状态 | 修正点 |
| --- | --- | --- | --- |
| ① 先出用例清单（正常/边界/异常） | ticket 的验收勾选项 + `测试：` 行（`docs/plans/<date>-<slug>.md`）；`docs/test-results/e2e-test-cases-*.xlsx` | 部分具备 | 现有 ticket 是能力描述式验收，**没有显式的正常/边界/异常三分**。新增需求时在 ticket 里补这三类，业务规则用例必须人工核验（AI 会编造"很合理但不存在"的用例） |
| ② 再写业务代码 | `tdd` 技能：**先与人对齐 seam**，再一条用例 → 一次最小实现（红→绿） | 具备，顺序要改 | 不是"先把测试全部写完再实现"——那是 `tdd` 技能点名的 horizontal slicing 反模式，会写出只测形状、对真实变更不敏感的测试。清单只用于**锁定范围**，实现走竖切 |
| ③ 生成单测跑通 | 后端 68 个测试类 / 94 个测试文件（`MockMvc standaloneSetup` + `src/test` 内存替身）；前端 vitest + jsdom + testing-library，40 个测试文件 | 完全具备 | 见 §1、§3 的命令与最小集 |
| ④ 补充集成测试脚本 | `scripts/e2e/run-e2e.sh`：真实本地库 + 真实登录会话驱动全业务闭环并逐项断言；另有 H2(`MODE=MySQL`) 上下文冒烟测试 | 具备且强于"补充脚本" | 集成测试在本仓库是**主要验证手段之一**，不是事后补丁。缺的是"何时扩 `flow.mjs`"的明文规则，见 §3 |

## 1. 四层验证设施与职责

| 层 | 位置 / 先例 | 替身策略 | 何时必须跑 |
| --- | --- | --- | --- |
| L1 领域与应用用例 | `backend/src/test/java/.../<module>/{domain,application}/` | 纯 JUnit 5 + AssertJ + `src/test` 内存替身 | 领域规则、状态流转、计算逻辑改动 |
| L2 接口层 | `.../api/*ControllerTest.java`，先例 `AssetControllerTest`：`MockMvcBuilders.standaloneSetup(...)` 注入服务与 `InMemory*` 替身 | 同 L1，禁止为测试在 `src/main` 造内存实现 | 接口出入参、校验、错误码改动 |
| L3 上下文冒烟 | `SimulationAssetApplicationTest`：`@SpringBootTest(webEnvironment = NONE)` + H2 `MODE=MySQL` | 真实 Spring 装配，无外部库 | 仓储 SQL、事务、配置、Bean 接线改动 |
| L4 端到端闭环 | `scripts/e2e/run-e2e.sh` → `flow.mjs`：真实本地库（`.env.local`，默认 `local` profile）、真实登录会话，覆盖资产生命周期 → 治理闭环 → 文档 → 检索 → 关联/收藏评论 | 仅上传 fixture 为生成文件，业务数据不走 mock | 跨模块业务闭环、真实库相关改动 |
| F 前端 | `frontend/src/**/<Component>.test.tsx`（与实现同目录），vitest + jsdom + `@testing-library/react`，setup 在 `frontend/src/test/setup.ts` | 断言用户可见行为，不查内部 state | 组件/页面/交互改动 |

常用命令：

```bash
# 仓库结构门禁（每次提交前，先跑）
scripts/check_repo_structure.sh

# 后端：单个测试类 / 模块 / 全量
cd backend
rtk mvn -Dtest=AssetControllerTest test
rtk mvn test                      # 共享契约、仓储、配置、领域改动时

# 前端：单文件 / 全量 / 日常门禁
cd frontend
pnpm exec vitest run src/features/auth/RequireAuth.test.tsx
pnpm test                         # vitest run 全量
pnpm lint && pnpm typecheck       # 日常提交门禁

# 端到端：真实本地库全流程
bash scripts/e2e/run-e2e.sh
```

`pnpm build` 仅在白名单触发时跑（路由/懒加载入口、Vite 配置、跨 feature 页面挂载、发布验收）。
UI 行为若没有自动化覆盖，需按 `AGENTS.md` 提供桌面视口的浏览器证据。

## 2. 新增需求的标准流程

1. **进流程**：`grilling` 收口需求 → `to-spec` → `to-tickets`，产物落 `docs/plans/<yyyy-mm-dd>-<slug>.md`
   （每票含 `Blocked by:` / `Status:` / 验收勾选项 / `测试：` 行）。
2. **对齐 seam 并写用例清单**：先确认"测哪个公开边界"，未确认的 seam 不写测试。
   在该 ticket 的 `测试：` 行按 **正常 / 边界 / 异常** 三分类列出用例——
   边界至少覆盖：空输入、重复提交、并发/重复请求、超时、下游失败、脏数据；
   异常至少覆盖：数据库失败、下游调用失败、权限不足。
   **业务规则用例（状态流转、统计口径、AssetScope 过滤）由人工核验**。
3. **竖切实现**：挑一条用例 → 写失败测试（红）→ 写最小实现（绿）→ 下一条。
   一条用例一次实现，不要先铺完一整层测试。
4. **跑最小集**：按 §3 决策表选命令，红了先修再继续。
5. **升级验证范围**：共享契约（DTO/枚举/错误码）、仓储 SQL、配置、领域规则改动 → 全量 `rtk mvn test`；
   跨模块闭环 → 扩 `flow.mjs` 阶段断言并跑 e2e。
6. **评审与提交**：`code-review`（Standards + Spec 双轴）→ 前后端分开提交 → 先跑 `scripts/check_repo_structure.sh`。
   重构与测试改名属于评审阶段，不塞进红绿循环。

## 3. 改动类型 → 必须跑的最小集合

| 改动类型 | 必须跑 |
| --- | --- |
| 单接口字段 / 单表 CRUD | 该模块 `*ControllerTest` + 对应 `*ServiceTest` |
| 领域规则、状态流转、统计计算 | 对应 domain/application 测试类 + 相邻用例；业务规则用例人工复核 |
| 共享契约（DTO / 枚举 / 错误码 / 对外 JSON 形状） | 全量 `rtk mvn test` + 前端 `pnpm typecheck` |
| 仓储 SQL / 索引 / 事务范围 | 全量 + L3 上下文冒烟 + `scripts/e2e/run-e2e.sh` |
| 前端组件 / 页面 / 交互 | `pnpm exec vitest run <file>` + `pnpm lint` + `pnpm typecheck` |
| 路由 / 懒加载 / Vite 配置 / 跨 feature 挂载 | 上述 + `pnpm build` |
| 跨模块业务闭环（资产→治理→文档→检索） | `scripts/e2e/run-e2e.sh`，并在 `flow.mjs` 补阶段断言 |
| 纯文案 / 样式 | `pnpm lint` + `pnpm typecheck`（+ 浏览器证据） |

`flow.mjs` 扩展规则：新需求若改变对外可见的业务闭环（新增状态、新增必经步骤、跨模块联动），
必须在 `flow.mjs` 对应阶段补断言；仅内部实现调整不扩。

## 4. 硬规则

- 测试替身只允许放 `src/test`；`src/main` 禁止内存仓储、种子数据、运行时 mock。
- 验证期不得连接或变更生产库；e2e 只用 `.env.local` 指向的本地库。
- 期望值必须有独立来源（写死的工作样例、spec 数字）；不要用与实现相同的公式重算一遍
  （tautological，永远绿）。
- 不 mock 内部协作者、不测私有方法、不从旁路（直查库）验证——换行为不变就不该红。
- 测试命名用领域语言；涉及 legacy 行为时对照
  `docs/archive/2026-09-07-doc-driven-development/root/CONTEXT.md` 的术语。
- 每个用例独立自证，覆盖：`AssetScope` 同域过滤、生命周期
  `草稿 → 待整理 → 已标准化 → 已停用`、legacy 主键与源值不被改写。
- 前端断言用户可见结果（文本、可访问角色、跳转），不断言内部 state。

## 5. 与通用清单的差异

逐条对照见 `docs/development-flow.md` §6。测试侧只有两处要纠正：

1. **顺序**：清单用于锁定范围并人工核验，实现走「一条用例一次实现」的竖切；
   不采用「先把测试全部写完再写代码」（`tdd` 技能点名的 horizontal slicing 反模式）。
2. **集成测试定位**：不是「最后补个脚本」，而是真实库上的主验证手段，跨模块需求默认要进
   `scripts/e2e/flow.mjs`。
