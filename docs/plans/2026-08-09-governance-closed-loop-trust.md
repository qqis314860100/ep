# 数据治理闭环可信度修复实施计划

## 目标

落实 `docs/specs/2026-08-09-data-governance-next-stage-design.md` 第一期：统一计划事实来源、修正甘特依赖方向、区分历史与闭环工作流，并将治理流程和计划工作区提升为任务详情核心内容。

## 约束

- 当前任务内联执行，不使用子代理。
- 保持默认内存后端配置，不连接生产数据库。
- 后端和前端分别提交；需求或设计修订单独提交。
- 先写能够复现问题的测试，再修改实现。
- 计划启动后的结构锁定、资产原值不可覆盖和同一 `AssetScope` 匹配规则保持不变。

## 版本 1：统一权威计划投影

### 文件

- 修改 `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceTaskApplicationService.java`
- 修改 `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceTaskController.java`
- 修改或新增 `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceTaskControllerTest.java`

### 实现

1. 添加接口测试：完成闭环任务的任务详情和 `/plans` 接口必须同时返回 `DONE` 和聚合完成量。
2. 将公开计划查询改为复用 `projection(task).plans()`，不再直接返回 `store.findPlans(taskId)`。
3. 计划响应统一使用现有 `PlanProjection` 结构，保留计划基线并提供聚合状态和完成量。
4. 确认历史任务仍返回历史计划状态和完成量。

### 验证

```bash
cd backend
rtk mvn -Dtest=GovernanceTaskControllerTest test
rtk mvn test
```

### 退出条件

- 同一任务在任务详情和计划接口中的状态、完成量一致。
- 后端完整测试通过。
- 单独提交后端版本。

## 版本 2：修正前端计划契约与依赖路径

### 文件

- 修改 `frontend/src/features/governance/types.ts`
- 修改 `frontend/src/features/governance/api.ts`
- 修改 `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx`
- 修改 `frontend/src/features/governance/tasks/GovernanceDependencyLayer.tsx`
- 修改 `frontend/src/features/governance/tasks/GovernanceGanttView.test.tsx`
- 新增或修改依赖路径纯函数测试

### 实现

1. 添加契约测试，前端将 `{ plan, status, completedQuantity }` 投影转换为单一 `GovernancePlan` 视图。
2. 提取依赖路径计算函数，测试正常、相邻、重叠和反向排期时最终水平段均向右。
3. SVG 只消费路径函数结果，箭头终点进入后续计划左侧。
4. 保留无效依赖提示，不为未知依赖绘制路径。

### 验证

```bash
cd frontend
rtk pnpm test -- src/features/governance/tasks/GovernanceGanttView.test.tsx
rtk pnpm typecheck
```

### 退出条件

- 任务 1 的 `dependency-101-102` 和 `dependency-102-103` 终段方向向右。
- 任务 4 甘特图显示聚合后的完成状态。
- 单独提交前端契约和路径版本。

## 版本 3：建立工作流版本感知的流程模型

### 文件

- 新增 `frontend/src/features/governance/tasks/governanceWorkflowModel.ts`
- 新增 `frontend/src/features/governance/tasks/governanceWorkflowModel.test.ts`
- 重构 `frontend/src/features/governance/tasks/GovernanceMilestoneStrip.tsx`
- 修改 `frontend/src/features/governance/tasks/GovernanceGanttView.tsx`
- 修改 `frontend/src/features/governance/types.ts`

### 实现

1. 用纯函数从 `workflowVersion`、任务状态、进度、当前轮次和工作台入口生成流程步骤。
2. `CLOSED_LOOP_V1` 展示编排、处理、确认、验收、应用、完成六阶段以及责任角色和数量摘要。
3. `REWORK_REQUIRED` 显示返工方向和轮次；缺少退回来源时使用中性文案。
4. `LEGACY_PROGRESS` 返回历史任务展示模型，不生成闭环阶段。
5. 将零散标签替换为连接式、可扫描的步骤条，颜色不是唯一状态信号。

### 验证

```bash
cd frontend
rtk pnpm test -- src/features/governance/tasks/governanceWorkflowModel.test.ts src/features/governance/tasks/GovernanceGanttView.test.tsx
rtk pnpm lint
rtk pnpm typecheck
```

### 退出条件

- 历史任务不显示闭环阶段。
- 每个闭环状态映射到唯一当前阶段。
- 返工状态不伪造退回来源。
- 单独提交前端流程版本。

## 版本 4：调整任务详情信息层级和默认视图

### 文件

- 修改 `frontend/src/features/governance/tasks/GovernancePlanEditor.tsx`
- 修改 `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx`
- 修改 `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`
- 按需要新增小型任务摘要组件

### 实现

1. 添加行为测试：草稿默认表格，执行中和后续状态默认甘特图，历史任务默认甘特图。
2. 页面顺序调整为任务摘要、流程/历史提示、计划工作区、阶段进度、范围与规则、问题集合。
3. 将范围快照转为业务摘要；原始 JSON 收入可展开技术详情。
4. 空问题集合使用紧凑空状态，不保留大块表格高度。
5. 保持编辑计划时自动切换回表格和任务启动后锁定。

### 验证

```bash
cd frontend
rtk pnpm test -- src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx
rtk pnpm test -- src/features/governance
rtk pnpm lint
rtk pnpm typecheck
rtk pnpm build
```

### 退出条件

- 核心流程与计划工作区进入首屏阅读顺序。
- 默认视图符合任务状态。
- 治理前端测试、lint、typecheck、build 通过。
- 单独提交前端页面版本。

## 版本 5：验收与需求收尾

### 文件

- 修改 `requirement.md`
- 必要时修订 `docs/technical-design.md` 的计划查询契约

### 实现

1. 将闭环可信度规则和验收项写入正式需求。
2. 更新计划查询接口为权威投影的技术说明。
3. 在 1366×768 和 1920×1080 下验证任务 1 与任务 4。
4. 检查箭头方向、流程语义、聚合进度、历史隔离、默认视图和页面溢出。

### 验证

```bash
git diff --check
cd frontend
rtk pnpm test
rtk pnpm lint
rtk pnpm typecheck
rtk pnpm build
cd ../backend
rtk mvn test
```

### 退出条件

- 设计中的 `P1-AC-01` 至 `P1-AC-08` 全部通过。
- 浏览器无控制台错误和页面级横向溢出。
- 相关文档单独提交，工作区洁净。
