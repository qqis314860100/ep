# 数据治理字段补充闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有治理任务编排基础上交付可运行的字段补充闭环：从问题池建单、固化范围、系统内清洗、业务确认、质量验收、返工到正式写入资产扩展数据，并让全部进度由治理项状态自动聚合。

**Architecture:** 以 `governance.task`、`governance.issue`、`governance.execution`、`governance.confirmation`、`governance.acceptance`、`governance.audit` 六个边界替换当前单体服务；应用服务只依赖端口，`dev` 使用内存适配器，`local/oceanbase` 使用 JDBC 适配器并继续受写开关保护。第一切片只实现 `DESCRIPTION`、`SPECIALTIES`、`OWNER`、`SCOPE` 四类字段结果，正式值只写扩展表，绝不覆盖 `sys_drawing` 原始字段。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring JDBC, JUnit 5, MockMvc, MySQL-compatible OceanBase, React 18, TypeScript 6, React Router, React Query, Ant Design, styled-components, Vitest, Testing Library, pnpm.

---

## Scope And File Map

本计划只覆盖共享闭环底座和第一个纵向切片“字段补充”。映射规则、文件归组、拆分合并、重复处理和持续治理看板分别使用后续实施计划；本计划只预留统一动作处理器和可扩展结果 JSON，不实现这些动作的页面或业务规则。

后端文件职责：

- `governance/task/domain/`：任务、计划、工作流版本、状态机、范围快照和聚合进度。
- `governance/task/application/`：任务建单、计划编排、启动门禁和查询投影。
- `governance/issue/`：字段问题、问题池查询和去重指纹。
- `governance/execution/`：治理项、结果版本、字段动作处理器、保存草稿、提交和批量逐项结果。
- `governance/confirmation/`：确认轮次、逐项决定和确认完成门禁。
- `governance/acceptance/`：策略快照、指标、抽样、返工和正式应用作业。
- `governance/audit/`：状态变化事件和治理报告投影。
- `governance/infrastructure/`：按 profile 提供内存与 JDBC 存储，以及只写扩展数据的资产端口实现。
- `governance/api/`：HTTP 请求/响应 record 和协议转换；不在控制器中直接改状态。

前端文件职责：

- `features/governance/types.ts`：唯一 API 类型来源。
- `features/governance/api.ts`：查询与命令封装、批量逐项结果和冲突解析。
- `features/governance/overview/`：阶段、自动进度和风险摘要。
- `features/governance/issues/`：问题筛选、勾选和建单。
- `features/governance/tasks/`：任务摘要、计划、分派和启动门禁。
- `features/governance/execution/`：治理项队列、字段编辑器和提交。
- `features/governance/confirmation/`：原值/拟值对比、通过和退回。
- `features/governance/acceptance/`：指标、固定抽样、结论和正式应用进度。
- `features/governance/shared/`：固定尺寸状态标签、阶段进度和逐项批量结果。

### Task 1: 建立闭环领域状态机和自动进度

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceWorkflowVersion.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceTaskStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernancePlanStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/domain/GovernanceItemStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/domain/GovernanceResultStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceProgress.java`
- Create: `backend/src/test/java/com/tianshu/assets/governance/task/domain/GovernanceWorkflowTest.java`

- [ ] **Step 1: 写失败的状态转换和进度测试**

```java
@Test
void rejectsSkippingBusinessConfirmation() {
    assertThatThrownBy(() -> GovernanceTaskStatus.IN_PROGRESS.moveTo(GovernanceTaskStatus.PENDING_ACCEPTANCE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("治理任务状态不能从 IN_PROGRESS 跳转到 PENDING_ACCEPTANCE");
}

@Test
void aggregatesProgressFromItemStates() {
    var progress = GovernanceProgress.from(List.of(
            GovernanceItemStatus.SUBMITTED,
            GovernanceItemStatus.CONFIRMED,
            GovernanceItemStatus.ACCEPTED,
            GovernanceItemStatus.BLOCKED,
            GovernanceItemStatus.REWORK_REQUIRED));

    assertThat(progress.total()).isEqualTo(5);
    assertThat(progress.submitted()).isEqualTo(3);
    assertThat(progress.confirmed()).isEqualTo(2);
    assertThat(progress.accepted()).isEqualTo(1);
    assertThat(progress.blocked()).isEqualTo(1);
    assertThat(progress.reworkRequired()).isEqualTo(1);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceWorkflowTest test`

Expected: FAIL，编译器报告 `GovernanceTaskStatus`、`GovernanceItemStatus` 和 `GovernanceProgress` 尚不存在。

- [ ] **Step 3: 实现明确的状态枚举和聚合规则**

```java
public enum GovernanceTaskStatus {
    DRAFT, IN_PROGRESS, PENDING_CONFIRMATION, PENDING_ACCEPTANCE, REWORK_REQUIRED, COMPLETED;

    public GovernanceTaskStatus moveTo(GovernanceTaskStatus target) {
        var allowed = switch (this) {
            case DRAFT -> Set.of(IN_PROGRESS);
            case IN_PROGRESS -> Set.of(PENDING_CONFIRMATION);
            case PENDING_CONFIRMATION -> Set.of(PENDING_ACCEPTANCE, REWORK_REQUIRED);
            case PENDING_ACCEPTANCE -> Set.of(COMPLETED, REWORK_REQUIRED);
            case REWORK_REQUIRED -> Set.of(IN_PROGRESS);
            case COMPLETED -> Set.<GovernanceTaskStatus>of();
        };
        if (!allowed.contains(target)) {
            throw new IllegalStateException("治理任务状态不能从 " + this + " 跳转到 " + target);
        }
        return target;
    }
}
```

```java
public record GovernanceProgress(
        int total, int submitted, int confirmed, int accepted, int blocked, int reworkRequired) {
    public static GovernanceProgress from(List<GovernanceItemStatus> states) {
        return new GovernanceProgress(
                states.size(),
                count(states, GovernanceItemStatus::countsAsSubmitted),
                count(states, GovernanceItemStatus::countsAsConfirmed),
                count(states, state -> state == GovernanceItemStatus.ACCEPTED),
                count(states, state -> state == GovernanceItemStatus.BLOCKED),
                count(states, state -> state == GovernanceItemStatus.REWORK_REQUIRED));
    }

    private static int count(List<GovernanceItemStatus> states, Predicate<GovernanceItemStatus> predicate) {
        return Math.toIntExact(states.stream().filter(predicate).count());
    }
}
```

`GovernanceItemStatus.countsAsSubmitted()` 对 `SUBMITTED`、`CONFIRMED`、`ACCEPTED` 返回 `true`；`countsAsConfirmed()` 只对 `CONFIRMED`、`ACCEPTED` 返回 `true`。`BLOCKED` 和 `REWORK_REQUIRED` 始终保留在总数分母中。

- [ ] **Step 4: 运行领域测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceWorkflowTest test`

Expected: PASS，2 tests，非法跨阶段跳转被拒绝且进度完全来自治理项状态。

- [ ] **Step 5: 提交领域底座**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/task/domain \
  backend/src/main/java/com/tianshu/assets/governance/execution/domain \
  backend/src/test/java/com/tianshu/assets/governance/task/domain/GovernanceWorkflowTest.java
git commit -m "feat: add governance closed-loop states"
```

### Task 2: 拆分任务存储并保留旧任务只读兼容

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceTask.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernancePlan.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceTaskStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceTaskApplicationService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceTaskStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceTaskStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/GovernanceStoreConfiguration.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceTaskController.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceTaskResponse.java`
- Delete: `backend/src/main/java/com/tianshu/assets/governance/application/GovernanceTaskService.java`
- Delete: `backend/src/main/java/com/tianshu/assets/governance/domain/GovernanceTask.java`
- Delete: `backend/src/main/java/com/tianshu/assets/governance/domain/GovernancePlan.java`
- Delete: `backend/src/main/java/com/tianshu/assets/governance/domain/GovernanceTaskStatus.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/task/application/GovernanceTaskLegacyCompatibilityTest.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceTaskControllerTest.java`

- [ ] **Step 1: 写失败的旧任务兼容测试**

```java
@Test
void legacyProgressTaskIsVisibleButCannotEnterClosedLoopCommands() {
    var store = InMemoryGovernanceTaskStore.withLegacySeed();
    var service = new GovernanceTaskApplicationService(store);

    var task = service.get(1L);

    assertThat(task.workflowVersion()).isEqualTo(GovernanceWorkflowVersion.LEGACY_PROGRESS);
    assertThat(task.legacyTotal()).isEqualTo(286);
    assertThatThrownBy(() -> service.requireClosedLoop(1L))
            .isInstanceOf(GovernanceTaskStateException.class)
            .hasMessage("历史进度任务为只读，请按问题池重新建单");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceTaskLegacyCompatibilityTest test`

Expected: FAIL，新的任务存储和 `workflowVersion` 尚未实现。

- [ ] **Step 3: 定义任务记录和存储端口**

```java
public record GovernanceTask(
        long id,
        String taskNumber,
        String name,
        String actionType,
        String issueType,
        String ownerUserId,
        String ownerName,
        String assigneeId,
        LocalDate dueDate,
        GovernanceTaskStatus status,
        int currentRound,
        GovernanceWorkflowVersion workflowVersion,
        Long scopeSnapshotId,
        Long qualityPolicySnapshotId,
        int legacyTotal,
        int legacyCompleted,
        long version) {}
```

```java
public interface GovernanceTaskStore {
    List<GovernanceTask> findAll();
    Optional<GovernanceTask> findById(long taskId);
    GovernanceTask insert(GovernanceTask task);
    GovernanceTask update(GovernanceTask task, long expectedVersion);
    List<GovernancePlan> findPlans(long taskId);
    GovernancePlan insertPlan(GovernancePlan plan);
}
```

`GovernanceStoreConfiguration` 在 `asset.governance-schema-enabled=false` 时装配内存治理存储并保留当前三条演示任务，全部标记为 `LEGACY_PROGRESS`；`dev` 默认走该分支。`JdbcGovernanceTaskStore` 使用 `@Profile({"local", "oceanbase"})` 和 `@ConditionalOnProperty(name="asset.governance-schema-enabled", havingValue="true")`，避免 OceanBase 尚未迁移 V1.7 时查询不存在的新表；写命令仍额外要求 `asset.database-writes-enabled=true`。

- [ ] **Step 4: 将控制器改为构造器注入新应用服务**

```java
public GovernanceTaskController(GovernanceTaskApplicationService service) {
    this.service = service;
}
```

`GovernanceTaskResponse` 增加 `workflowVersion`、`currentRound`、`version` 和 `editable`。`LEGACY_PROGRESS` 的 `total/completed` 继续返回旧列并始终 `editable=false`；`CLOSED_LOOP_V1` 的这两个字段由后续 `GovernanceProgress` 投影提供。旧 `PATCH /progress`、计划更新和启动命令对历史任务统一返回状态冲突。

- [ ] **Step 5: 运行旧接口和兼容测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceTaskLegacyCompatibilityTest,GovernanceTaskControllerTest test`

Expected: PASS；旧任务仍可查询但不能再修改完成量、计划或状态，闭环命令也拒绝旧任务。

- [ ] **Step 6: 提交任务存储拆分**

```bash
git add backend/src/main/java/com/tianshu/assets/governance \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "refactor: split governance task storage"
```

### Task 3: 建立字段问题池并按问题集合创建闭环任务

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/issue/domain/GovernanceField.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/issue/domain/GovernanceIssue.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/issue/domain/GovernanceIssueStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/issue/application/GovernanceIssueStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/issue/application/GovernanceIssueService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceIssueStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceIssueStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceIssueController.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceTaskController.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/issue/application/GovernanceIssueServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceIssueControllerTest.java`

- [ ] **Step 1: 写失败的问题去重和建单测试**

```java
@Test
void createsTaskFromOpenIssuesAndDerivesTotalFromIssueCount() {
    var issueStore = InMemoryGovernanceIssueStore.withFieldSeeds();
    var taskStore = new InMemoryGovernanceTaskStore();
    var service = new GovernanceIssueService(issueStore, taskStore);

    var task = service.createTask(new CreateGovernanceTaskCommand(
            "历史字段补充", List.of(1001L, 1002L), "emp-chen", "陈工",
            LocalDate.of(2026, 9, 1)));

    assertThat(task.workflowVersion()).isEqualTo(GovernanceWorkflowVersion.CLOSED_LOOP_V1);
    assertThat(task.legacyTotal()).isZero();
    assertThat(issueStore.findClaimedByTask(task.id())).hasSize(2);
}

@Test
void refusesClaimingTheSameIssueTwice() {
    var command = new CreateGovernanceTaskCommand(
            "单项字段补充", List.of(1001L), "emp-chen", "陈工",
            LocalDate.of(2026, 9, 1));
    service.createTask(command);
    assertThatThrownBy(() -> service.createTask(command))
            .isInstanceOf(GovernanceConflictException.class)
            .hasMessage("问题已被其他治理任务纳入");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceIssueServiceTest,GovernanceIssueControllerTest test`

Expected: FAIL，问题对象、存储和 `POST /api/v1/governance/tasks` 的 `issueIds` 契约尚不存在。

- [ ] **Step 3: 实现问题对象和去重指纹**

```java
public enum GovernanceField { DESCRIPTION, SPECIALTIES, OWNER, SCOPE }

public record GovernanceIssue(
        long id, long assetId, GovernanceField targetField, String issueType,
        String targetPath, String ruleCode, long ruleVersion, String originalFactJson,
        long assetVersion, String scopeFingerprint, boolean blocking,
        GovernanceIssueStatus status, Long taskId, long version) {
    public String fingerprint() {
        return assetId + "|" + issueType + "|" + targetPath + "|" + ruleVersion;
    }
}
```

`InMemoryGovernanceIssueStore.withFieldSeeds()` 提供四类问题样本。JDBC 插入时以 `fingerprint` 唯一索引防止重复；领取问题使用 `WHERE status = 'OPEN' AND version = :expectedVersion`，受影响行数不是 1 时抛出 `GovernanceConflictException`。

- [ ] **Step 4: 替换建单请求中的手工总量**

```java
public record CreateTaskRequest(
        @NotBlank String name,
        @NotEmpty List<Long> issueIds,
        @NotBlank String ownerUserId,
        @NotBlank String ownerName,
        @NotNull LocalDate dueDate) {}
```

新增 `GET /api/v1/governance/issues?field=&status=&assetId=`，响应包含问题 ID、资产摘要、目标字段、原始事实、严重度、阻断标记和版本。新建任务只接受问题 ID；服务在同一事务内创建 `CLOSED_LOOP_V1` 任务并把问题状态改为 `CLAIMED`。

- [ ] **Step 5: 运行问题池测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceIssueServiceTest,GovernanceIssueControllerTest test`

Expected: PASS；任务总量不再由客户端传入，同一问题不能进入两个活动任务。

- [ ] **Step 6: 提交问题池和建单契约**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/issue \
  backend/src/main/java/com/tianshu/assets/governance/infrastructure \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: create governance tasks from issues"
```

### Task 4: 计划分配、依赖校验和启动范围固化

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceScopeSnapshot.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceScopeItem.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernanceRuleSnapshot.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceWorkflowStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceRuleCatalog.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceTaskStartService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceWorkflowStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceRuleCatalog.java`
- Create: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/task/domain/GovernancePlan.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceTaskController.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/task/application/GovernanceTaskStartServiceTest.java`

- [ ] **Step 1: 写失败的启动门禁测试**

```java
@Test
void startRequiresEveryIssueAssignedExactlyOnceAndAcyclicDependencies() {
    var task = fixture.closedLoopDraftWithTwoIssues();
    fixture.addPlan(task.id(), 11L, List.of(1001L), List.of(12L));
    fixture.addPlan(task.id(), 12L, List.of(1001L, 1002L), List.of(11L));

    assertThatThrownBy(() -> service.start(task.id(), task.version()))
            .isInstanceOf(GovernanceValidationException.class)
            .hasMessageContaining("治理项不能重复分配")
            .hasMessageContaining("计划依赖不能形成环");
}

@Test
void startFreezesIssueAssetAndRuleVersions() {
    var task = fixture.validDraft();
    var started = service.start(task.id(), task.version());

    assertThat(started.status()).isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
    assertThat(workflowStore.scopeItems(started.scopeSnapshotId()))
            .extracting(GovernanceScopeItem::assetVersion, GovernanceScopeItem::ruleVersion)
            .containsExactly(tuple(7L, 3L), tuple(9L, 3L));
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceTaskStartServiceTest test`

Expected: FAIL，范围快照、计划-问题关联和依赖图校验尚未实现。

- [ ] **Step 3: 实现计划模型和图校验**

```java
public record GovernancePlan(
        long id, long taskId, int sequence, String name, String responsibleUserId,
        LocalDate startDate, LocalDate dueDate, GovernancePlanStatus status,
        List<Long> dependencyIds, List<Long> issueIds, long version) {}
```

`GovernanceTaskStartService.start()` 按顺序执行：校验任务为 `DRAFT` 和期望版本；校验每个计划都有责任人和日期；校验依赖属于同任务、不依赖自身、深度优先遍历无环；校验每个已领取问题恰好出现在一个计划；重新读取问题和资产版本；从 `GovernanceRuleCatalog` 读取启用的数据标准、字段规则及所用字典版本；保存不可变范围快照、规则快照、质量策略快照和治理项；最后以乐观锁将任务转为 `IN_PROGRESS`。

计划请求只接受名称、责任人、起止日期、依赖计划 ID 和问题 ID；计划数量由 `issueIds.size()` 派生，不接受 `plannedQuantity/completedQuantity`。删除直接修改计划执行状态的 API，计划状态在 Task 6 中由治理项和依赖聚合。

同时创建 `GovernanceTestFixture`，先提供 `closedLoopDraftWithTwoIssues()`、`validDraft()` 和 `addPlan(taskId, planId, issueIds, dependencyIds)`。夹具通过 Task 2 和 Task 3 的公开应用服务创建任务、问题和计划，不直接改领域对象；后续任务在同一文件中逐步增加进入执行、确认、验收和完成态的公开方法。

- [ ] **Step 4: 增加明确启动命令**

```java
@PostMapping("/{taskId}/start")
public GovernanceTaskResponse start(
        @PathVariable long taskId,
        @Valid @RequestBody VersionRequest request) {
    return GovernanceTaskResponse.from(startService.start(taskId, request.version()));
}
```

移除闭环任务对通用 `PATCH /status` 的依赖。该旧接口只用于验证历史调用会收到只读状态冲突，不允许驱动任何任务状态。

- [ ] **Step 5: 运行启动门禁测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceTaskStartServiceTest,GovernanceTaskControllerTest test`

Expected: PASS；重复分配、漏分配、跨任务依赖、自依赖和环形依赖均阻止启动，成功启动后计划结构锁定。

- [ ] **Step 6: 提交范围固化能力**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/task \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: freeze governance scope on start"
```

### Task 5: 保存字段结果草稿并逐项提交

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/domain/GovernanceItem.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/domain/GovernanceResultVersion.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/application/GovernanceActionHandler.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/application/FieldSupplementActionHandler.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/application/GovernanceExecutionStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/application/GovernanceExecutionService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceExecutionStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceExecutionController.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/execution/application/GovernanceExecutionServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceExecutionControllerTest.java`

- [ ] **Step 1: 写失败的字段校验、版本和提交测试**

```java
@Test
void savesDraftWithoutChangingOfficialAssetAndSubmitsImmutableVersion() {
    var item = fixture.descriptionItem();

    var draft = service.saveDraft(item.id(), new SaveResultDraftCommand(
            item.version(), item.assetVersion(), "{\"description\":\"焊接工位设备总成\"}", "emp-chen"));
    var submitted = service.submit(item.id(), draft.id(), draft.version(), "emp-chen");

    assertThat(submitted.status()).isEqualTo(GovernanceResultStatus.SUBMITTED);
    assertThat(executionStore.item(item.id()).status()).isEqualTo(GovernanceItemStatus.SUBMITTED);
    assertThat(assetPort.officialDescription(item.assetId())).isEmpty();
    assertThatThrownBy(() -> service.saveDraft(item.id(), fixture.commandFor(submitted)))
            .isInstanceOf(GovernanceConflictException.class);
}

@Test
void rejectsScopeWhoseProductAndProductionPartsComeFromDifferentScopes() {
    assertThatThrownBy(() -> handler.validate(
            GovernanceField.SCOPE, fixture.mixedScopeJson(), fixture.scopeContext()))
            .isInstanceOf(GovernanceValidationException.class)
            .hasMessage("产品与生产条件必须来自同一个适用范围");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceExecutionServiceTest,GovernanceExecutionControllerTest test`

Expected: FAIL，治理项、结果版本和字段动作处理器尚不存在。

- [ ] **Step 3: 实现结果版本和字段处理器**

```java
public record GovernanceResultVersion(
        long id, long itemId, int governanceRound, int resultVersion,
        GovernanceField field, String originalValueJson, String proposedValueJson,
        long standardVersion, Map<String, Long> dictionaryVersions,
        GovernanceResultStatus status, String actorUserId,
        Instant savedAt, Instant submittedAt, long version) {}
```

`FieldSupplementActionHandler` 使用 Jackson 解析 JSON，不使用字符串拼接。四类结果结构固定为：

```json
{"description":"焊接工位设备总成"}
{"specialtyItemIds":[201,202]}
{"ownerUserId":"emp-chen","ownerName":"陈工"}
{"scopes":[{"platformFamily":"乘用车","platformVariant":"底部水冷","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}]}
```

说明不能为空；专业字典项必须启用且版本匹配；负责人必须来自员工目录；范围层级必须在同一个 `AssetScope` 内完整匹配。保存草稿不触碰资产正式值，提交后该结果版本不可修改。

测试夹具增加 `descriptionItem()`、`scopeContext()`、`mixedScopeJson()` 和 `commandFor(result)`，全部通过新建的执行应用服务保存状态，供本任务测试复用。

- [ ] **Step 4: 实现明确执行 API**

```java
@PutMapping("/items/{itemId}/result-draft")
public GovernanceResultResponse saveDraft(@PathVariable long itemId,
        @Valid @RequestBody SaveResultDraftRequest request) {
    return GovernanceResultResponse.from(service.saveDraft(itemId, request.toCommand()));
}

@PostMapping("/items/{itemId}/submit")
public GovernanceResultResponse submit(@PathVariable long itemId,
        @Valid @RequestBody SubmitResultRequest request) {
    return GovernanceResultResponse.from(service.submit(
            itemId, request.resultVersionId(), request.resultVersion(), request.actorUserId()));
}

public record SaveResultDraftRequest(
        @Min(0) long itemVersion,
        @Min(0) long assetVersion,
        @NotNull JsonNode proposedValue,
        @NotBlank String actorUserId) {
    SaveResultDraftCommand toCommand() {
        return new SaveResultDraftCommand(
                itemVersion, assetVersion, proposedValue.toString(), actorUserId);
    }
}

public record SubmitResultRequest(
        @Min(1) long resultVersionId,
        @Min(0) long resultVersion,
        @NotBlank String actorUserId) {}
```

`GET /api/v1/governance/tasks/{taskId}/items` 返回治理项、当前结果、原始事实、规则上下文、退回意见和版本。资产版本或结果版本不匹配时返回 `409 governance_version_conflict`。

- [ ] **Step 5: 运行执行测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceExecutionServiceTest,GovernanceExecutionControllerTest test`

Expected: PASS；草稿可反复按版本保存，已提交结果不可原地修改，正式资产值保持不变。

- [ ] **Step 6: 提交字段执行能力**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/execution \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: execute field governance items"
```

### Task 6: 增加批量逐项结果和自动进入待确认

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/execution/application/BatchItemResult.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/execution/application/GovernanceExecutionService.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceExecutionController.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceTaskApplicationService.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/execution/application/GovernanceBatchExecutionTest.java`

- [ ] **Step 1: 写失败的部分成功和阶段门禁测试**

```java
@Test
void batchReturnsSuccessValidationFailureAndConflictIndependently() {
    var response = service.batchResults("batch-20260726-001", List.of(
            fixture.validDescriptionCommand(501L),
            fixture.blankDescriptionCommand(502L),
            fixture.staleVersionCommand(503L)));

    assertThat(response.results()).extracting(BatchItemResult::outcome)
            .containsExactly(SUCCESS, VALIDATION_FAILED, CONFLICT);
    assertThat(executionStore.item(501L).status()).isEqualTo(GovernanceItemStatus.SUBMITTED);
}

@Test
void taskCanEnterConfirmationOnlyWhenEveryItemIsSubmittedAndNoneIsBlocked() {
    var task = fixture.validStartedTask();
    fixture.markSubmitted(501L);
    fixture.markBlocked(502L, "负责人无法确认");
    assertThatThrownBy(() -> taskService.submitForConfirmation(task.id(), task.version()))
            .hasMessage("仍有阻塞或未提交治理项");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceBatchExecutionTest test`

Expected: FAIL，批量结果和 `submit-for-confirmation` 命令尚不存在。

- [ ] **Step 3: 实现幂等批量命令**

```java
public record BatchItemResult(
        long itemId, BatchOutcome outcome, Long resultVersionId,
        String errorCode, String message, Long currentVersion) {}
```

`batchResults(idempotencyKey, commands)` 对每个治理项开启独立事务边界；同一幂等键和相同请求摘要直接返回已保存结果，不重复创建结果版本。批量限制为同一目标字段、同一标准版本和同一 `scopeFingerprint`，否则该条返回 `VALIDATION_FAILED`。

测试夹具增加 `validStartedTask()`、`markSubmitted(itemId)`、`markBlocked(itemId, reason)`、`validDescriptionCommand(itemId)`、`blankDescriptionCommand(itemId)` 和 `staleVersionCommand(itemId)`，分别通过执行服务触发真实成功、校验失败和版本冲突。

- [ ] **Step 4: 从治理项聚合计划和任务进度**

删除闭环任务的 `PATCH /progress` 能力。`GovernanceTaskApplicationService.detail()` 使用 `GovernanceProgress.from(itemStates)`；计划状态为：依赖未完成时 `BLOCKED`，存在处理中治理项时 `IN_PROGRESS`，全部达到 `SUBMITTED` 及之后时 `DONE`，其他为 `NOT_STARTED`。

`GET /api/v1/governance/tasks/{taskId}` 返回任务、范围/规则/质量策略快照摘要、三段进度、风险数量、计划聚合状态和各工作台入口；该详情是总览和任务详情页的唯一任务投影来源。

`GET /api/v1/governance/tasks` 接受 `status`、`ownerUserId`、`dueBefore`、`field` 和 `scopeFingerprint` 筛选；每个条件在同一任务投影上组合，范围筛选不跨 `AssetScope` 拼接。

- [ ] **Step 5: 运行批量和任务测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceBatchExecutionTest,GovernanceTaskControllerTest test`

Expected: PASS；单条失败不回滚成功项，闭环任务进度无法手工修改，阻塞项阻止进入确认。

- [ ] **Step 6: 提交批量和自动进度**

```bash
git add backend/src/main/java/com/tianshu/assets/governance \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: derive governance progress from items"
```

### Task 7: 完成业务确认轮次和确认退回

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/confirmation/domain/GovernanceConfirmationRound.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/confirmation/domain/GovernanceConfirmationDecision.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/confirmation/application/GovernanceConfirmationStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/confirmation/application/AssetResponsibilityPort.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/confirmation/application/GovernanceConfirmationService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryAssetResponsibilityAdapter.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceConfirmationStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceConfirmationController.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/confirmation/application/GovernanceConfirmationServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceConfirmationControllerTest.java`

- [ ] **Step 1: 写失败的逐项决定和完成轮次测试**

```java
@Test
void confirmationRequiresCoverageAndCreatesReworkForRejectedItems() {
    var round = fixture.pendingConfirmationWithTwoItems();
    service.decide(round.id(), 501L, new DecisionCommand(APPROVED, "", 0L, "owner-1"));

    assertThatThrownBy(() -> service.complete(round.taskId(), round.id(), round.version()))
            .hasMessage("确认决定尚未覆盖全部治理项");

    service.decide(round.id(), 502L, new DecisionCommand(REJECTED, "专业类别不符合资产用途", 0L, "owner-2"));
    var result = service.complete(round.taskId(), round.id(), round.version());

    assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.REWORK_REQUIRED);
    assertThat(executionStore.item(502L).status()).isEqualTo(GovernanceItemStatus.REWORK_REQUIRED);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceConfirmationServiceTest,GovernanceConfirmationControllerTest test`

Expected: FAIL，确认轮次、决定和 API 尚不存在。

- [ ] **Step 3: 实现轮次内不可覆盖的确认记录**

```java
public record GovernanceConfirmationDecision(
        long id, long roundId, long itemId, long resultVersionId,
        ConfirmationDecision decision, String comment, String confirmerUserId,
        Instant decidedAt, long version) {
    public GovernanceConfirmationDecision {
        if (decision == ConfirmationDecision.REJECTED && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("退回必须填写确认意见");
        }
    }
}
```

确认人必须由 `AssetResponsibilityPort` 验证为该资产当前有效责任人；批量通过仅允许同一结果类型和同一责任范围。完成轮次前校验所有当前轮次 `SUBMITTED` 结果均有决定；存在退回则仅把对应治理项改为 `REWORK_REQUIRED`，通过项保持 `CONFIRMED`，任务进入 `REWORK_REQUIRED`；全部通过则进入 `PENDING_ACCEPTANCE`。

测试夹具增加 `pendingConfirmationWithTwoItems()`，通过保存和提交两个真实结果、执行 `submitForConfirmation` 得到当前确认轮次，不直接构造待确认状态。

- [ ] **Step 4: 实现确认 API**

实现设计文档中的三个接口：查询当前轮次、保存单项决定、完成轮次。响应必须同时返回确认覆盖率、通过率和每条决定版本；不返回无权限资产的原值或拟值。

- [ ] **Step 5: 运行确认测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceConfirmationServiceTest,GovernanceConfirmationControllerTest test`

Expected: PASS；部分决定可保存，未覆盖不能完成，退回意见必填且轮次历史不被覆盖。

- [ ] **Step 6: 提交业务确认**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/confirmation \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: add governance confirmation rounds"
```

### Task 8: 计算质量指标并固化验收抽样

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceQualityMetric.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceQualityPolicySnapshot.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceAcceptanceRound.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceAcceptanceMetricResult.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceAcceptanceSample.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceAcceptanceStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceQualityService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceAcceptanceStore.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/acceptance/application/GovernanceQualityServiceTest.java`

- [ ] **Step 1: 写失败的指标口径和固定抽样测试**

```java
@Test
void zeroDenominatorIsNotApplicableInsteadOfOneHundredPercent() {
    var result = service.calculate(
            STANDARD_DICTIONARY_HIT_RATE, List.of(), fixture.policyAllowingNotApplicable());
    assertThat(result.denominator()).isZero();
    assertThat(result.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
    assertThat(result.passed()).isTrue();
}

@Test
void acceptanceSampleCannotBeRegeneratedAfterAFailedCheck() {
    var round = fixture.openAcceptanceRound();
    var originalIds = round.samples().stream().map(GovernanceAcceptanceSample::itemId).toList();
    service.saveSample(round.id(), originalIds.getFirst(), false, "功能说明与现场用途不符", "qa-1", 0L);

    assertThat(service.currentRound(round.taskId()).samples())
            .extracting(GovernanceAcceptanceSample::itemId)
            .containsExactlyElementsOf(originalIds);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceQualityServiceTest test`

Expected: FAIL，质量策略、指标结果和抽样模型尚不存在。

- [ ] **Step 3: 实现五个首期指标**

```java
public enum GovernanceQualityMetric {
    REQUIRED_FIELD_COMPLETENESS,
    ASSET_SCOPE_VALIDITY,
    STANDARD_DICTIONARY_HIT_RATE,
    OWNER_COVERAGE,
    SAMPLE_ACCURACY
}
```

每个指标保存 `numerator`、`denominator`、`value`、`threshold`、`applicability`、`passed`。分母为零时 `value=null` 且标记 `NOT_APPLICABLE`；是否通过读取任务启动时固化的 `notApplicablePasses`。抽样使用 `taskId + governanceRound + policyVersion` 作为稳定随机种子，生成后持久化，页面刷新或保存失败样本均不得改变集合。

测试夹具增加 `scopeContext()`、`policyAllowingNotApplicable()` 和 `openAcceptanceRound()`，质量服务测试只从夹具读取标准、字典、责任和范围事实。

- [ ] **Step 4: 运行质量测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceQualityServiceTest test`

Expected: PASS；五个指标均按统一分子分母计算，固定抽样不可被失败后重抽规避。

- [ ] **Step 5: 提交质量策略和抽样**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/acceptance \
  backend/src/test/java/com/tianshu/assets/governance/acceptance
git commit -m "feat: calculate governance acceptance quality"
```

### Task 9: 完成质量验收、退回和新返工轮次

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceAcceptanceService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/task/application/GovernanceReworkService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceAcceptanceController.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/execution/domain/GovernanceResultVersion.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/acceptance/application/GovernanceAcceptanceServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/task/application/GovernanceReworkServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceAcceptanceControllerTest.java`

- [ ] **Step 1: 写失败的验收结论和返工版本测试**

```java
@Test
void failedMetricReturnsOnlyAffectedItemsToRework() {
    var round = fixture.pendingAcceptance();
    fixture.failMetric(round.id(), GovernanceQualityMetric.OWNER_COVERAGE, List.of(502L));

    var result = service.complete(round.taskId(), round.id(), round.version(), "qa-1");

    assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.REWORK_REQUIRED);
    assertThat(executionStore.item(501L).status()).isEqualTo(GovernanceItemStatus.CONFIRMED);
    assertThat(executionStore.item(502L).status()).isEqualTo(GovernanceItemStatus.REWORK_REQUIRED);
}

@Test
void reworkSupersedesRejectedResultAndKeepsHistory() {
    var task = fixture.reworkRequiredTask();
    var reopened = reworkService.open(task.id(), task.version(), "owner fixed", "emp-chen");

    assertThat(reopened.currentRound()).isEqualTo(task.currentRound() + 1);
    assertThat(executionStore.resultsForItem(502L))
            .extracting(GovernanceResultVersion::status)
            .containsExactly(GovernanceResultStatus.SUPERSEDED, GovernanceResultStatus.DRAFT);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceAcceptanceServiceTest,GovernanceReworkServiceTest,GovernanceAcceptanceControllerTest test`

Expected: FAIL，验收完成命令和返工轮次尚未实现。

- [ ] **Step 3: 实现验收完成门禁**

完成验收前必须满足：全部确认项均有指标覆盖；策略要求抽样时全部固定样本均已检查；失败指标能解析出受影响治理项；退回样本有问题说明。存在失败时，受影响治理项进入 `REWORK_REQUIRED`，其他项保持 `CONFIRMED`，任务进入 `REWORK_REQUIRED`。全部通过时，把全部治理项标为 `ACCEPTED`，验收轮次标为 `PASSED`，但任务仍保持 `PENDING_ACCEPTANCE`，并创建正式应用作业。

测试夹具增加 `pendingAcceptance()`、`failMetric(roundId, metric, itemIds)` 和 `reworkRequiredTask()`，都通过确认与验收服务推进真实轮次。

- [ ] **Step 4: 实现返工开启命令**

`POST /api/v1/governance/tasks/{taskId}/rework` 接受任务版本、返工说明和操作人。服务只为 `REWORK_REQUIRED` 项生成新轮次结果草稿；上一轮结果变为 `SUPERSEDED`，通过项不重复处理。任务 `currentRound + 1` 后回到 `IN_PROGRESS`，确认和验收记录继续按原轮次只读可查。

- [ ] **Step 5: 实现验收 API 并运行测试**

实现查询当前验收轮次、保存样本结果、完成验收和开启返工四个接口。

Run: `cd backend && rtk mvn -Dtest=GovernanceAcceptanceServiceTest,GovernanceReworkServiceTest,GovernanceAcceptanceControllerTest test`

Expected: PASS；失败只退回受影响项，新轮次不覆盖旧结果，验收通过后等待正式应用。

- [ ] **Step 6: 提交验收和返工**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/acceptance \
  backend/src/main/java/com/tianshu/assets/governance/task \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: add governance acceptance and rework"
```

### Task 10: 通过幂等作业正式写入资产扩展值

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceOperationJob.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/domain/GovernanceOperationJobItem.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceAssetPort.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceApplicationJobService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/acceptance/application/GovernanceJobDispatcher.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceAssetAdapter.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceAssetAdapter.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/SpringGovernanceJobDispatcher.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/GovernanceJobConfiguration.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceJobController.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/acceptance/application/GovernanceApplicationJobServiceTest.java`

- [ ] **Step 1: 写失败的幂等、部分失败和资产状态测试**

```java
@Test
void applyKeepsSuccessesAndRetriesOnlyFailedItems() {
    var job = fixture.acceptedTaskWithTwoItems();
    assetPort.failNextApplyFor(502L, "extension row locked");

    var first = service.run(job.id());
    assertThat(first.succeeded()).isEqualTo(1);
    assertThat(first.failed()).isEqualTo(1);

    var second = service.retry(job.id());
    assertThat(second.succeeded()).isEqualTo(2);
    assertThat(assetPort.applyCount(501L)).isEqualTo(1);
    assertThat(assetPort.applyCount(502L)).isEqualTo(1);
}

@Test
void taskCompletionDoesNotStandardizeAssetWithAnotherBlockingIssue() {
    var assetId = 104L;
    var job = fixture.acceptedJobFor(assetId);
    fixture.addOpenBlockingIssue(assetId, "MISSING_PRIMARY_FILE");
    service.run(job.id());

    assertThat(assetPort.status(assetId)).isEqualTo(AssetStatus.PENDING_CURATION);
    assertThat(taskStore.findById(job.taskId()).orElseThrow().status())
            .isEqualTo(GovernanceTaskStatus.COMPLETED);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceApplicationJobServiceTest test`

Expected: FAIL，正式应用作业和专用资产端口尚不存在。

- [ ] **Step 3: 定义只写扩展数据的资产端口**

```java
public interface GovernanceAssetPort {
    GovernanceAssetSnapshot snapshot(long assetId);
    ApplyOutcome applyFieldResult(long itemId, long assetId, GovernanceField field,
            String proposedValueJson, long expectedAssetVersion, String actorUserId);
    boolean meetsAllActiveStandards(long assetId);
    void markStandardized(long assetId, long expectedAssetVersion, String actorUserId);
}
```

`JdbcGovernanceAssetAdapter` 的字段落点固定为：`DESCRIPTION -> asset_package_ext.standard_description`，`SPECIALTIES -> asset_package_ext.standard_specialties`，`OWNER -> asset_responsibility_ext`，`SCOPE -> asset_scope_ext` 新的标准来源行。它不得调用 `OceanBaseAssetRepository.update()`，不得更新 `sys_drawing.drawing_content`、`drawing_column`、`created_by_name` 或旧范围文本。

- [ ] **Step 4: 实现逐项作业状态和重试**

作业项状态为 `PENDING`、`SUCCEEDED`、`FAILED`。每次运行只读取 `PENDING/FAILED` 项；先校验资产版本和结果版本，再调用动作处理器与资产端口；成功后将结果版本标为 `APPLIED`、对应问题标为 `RESOLVED`，并向 `asset_audit_ext` 写入不含敏感数据的标准值变更摘要。全部作业项成功后，逐资产运行“所有启用标准通过且无开放阻断问题”的判断，满足才标记 `STANDARDIZED`，最后任务进入 `COMPLETED`。

测试夹具增加 `acceptedTaskWithTwoItems()`、`acceptedJobFor(assetId)`、`addOpenBlockingIssue(assetId, issueType)`，内存资产端口增加仅测试可用的 `failNextApplyFor(itemId, reason)`、`applyCount(itemId)` 和 `status(assetId)` 查询。

验收事务只创建作业并发布作业 ID。`SpringGovernanceJobDispatcher` 使用命名为 `governanceJobExecutor` 的有界 `TaskExecutor`，在事务提交后调用 `GovernanceApplicationJobService.run(jobId)`；应用服务先以乐观锁认领作业，保证重复事件和手工重试不会并发处理同一作业。测试直接调用应用服务，不依赖线程时序。

- [ ] **Step 5: 实现作业查询并运行测试**

`GET /api/v1/governance/jobs/{jobId}` 返回总数、成功、失败、处理中、错误原因和是否可重试。重复运行成功项不得重复写入扩展表或重复增加资产版本。

Run: `cd backend && rtk mvn -Dtest=GovernanceApplicationJobServiceTest test`

Expected: PASS；局部失败保留成功结果，重试仅处理失败项，任务完成与资产标准化判定相互独立。

- [ ] **Step 6: 提交正式应用作业**

```bash
git add backend/src/main/java/com/tianshu/assets/governance/acceptance \
  backend/src/main/java/com/tianshu/assets/governance/infrastructure \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: apply accepted governance results"
```

### Task 11: 记录审计事件并生成闭环报告

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/audit/domain/GovernanceAuditEvent.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/audit/application/GovernanceAuditStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/audit/application/GovernanceAuditService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/audit/application/GovernanceReportService.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/InMemoryGovernanceAuditStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/api/GovernanceHistoryController.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/audit/application/GovernanceReportServiceTest.java`

- [ ] **Step 1: 写失败的历史完整性测试**

```java
@Test
void reportKeepsOriginalProposedDecisionAcceptanceAndApplyFacts() {
    var completedTask = fixture.completedFieldTask();

    var report = service.report(completedTask.id());

    assertThat(report.rounds()).hasSize(2);
    assertThat(report.items().getFirst().originalValueJson()).contains("旧说明");
    assertThat(report.items().getFirst().appliedValueJson()).contains("标准说明");
    assertThat(report.items().getFirst().confirmationDecisions()).hasSize(2);
    assertThat(report.applicationSummary().failed()).isZero();
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceReportServiceTest test`

Expected: FAIL，审计存储和报告投影尚不存在。

- [ ] **Step 3: 实现统一审计事件**

```java
public record GovernanceAuditEvent(
        long id, long taskId, Long itemId, String aggregateType, long aggregateId,
        String action, int governanceRound, String actorUserId,
        String beforeJson, String afterJson, Instant createdAt) {}
```

任务启动、计划锁定、结果保存/提交、确认决定、验收决定、返工开启、正式应用成功/失败和任务完成均写事件。事件 JSON 只记录字段值、状态、版本和错误码，不记录文件内容、凭证、请求头或永久存储地址。

测试夹具增加 `completedFieldTask()`，用公开命令完成“第一轮确认退回、第二轮确认和验收通过、正式应用完成”的完整场景。

- [ ] **Step 4: 实现历史与报告 API**

`GET /api/v1/governance/tasks/{taskId}/history` 按时间返回事件；`GET /api/v1/governance/tasks/{taskId}/report` 返回范围快照、各轮次数量、进度、原值/拟值/正式值、确认与验收摘要、返工原因和作业结果。

- [ ] **Step 5: 运行报告测试并提交**

Run: `cd backend && rtk mvn -Dtest=GovernanceReportServiceTest test`

Expected: PASS；跨两轮返工仍能完整追踪每条治理项。

```bash
git add backend/src/main/java/com/tianshu/assets/governance/audit \
  backend/src/main/java/com/tianshu/assets/governance/api \
  backend/src/test/java/com/tianshu/assets/governance/audit
git commit -m "feat: add governance audit and reports"
```

### Task 12: 增加闭环迁移和 JDBC 持久化

**Files:**
- Create: `docs/migrations/V1_7__governance_closed_loop_schema.sql`
- Create: `docs/migrations/local/V1_7__local_governance_closed_loop.sql`
- Modify: `docs/migrations/local/V1_5__local_seed.sql`
- Modify: `docs/local-development.md`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-oceanbase.yml`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceTaskStore.java`
- Modify: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceIssueStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceWorkflowStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceExecutionStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceConfirmationStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceAcceptanceStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceAuditStore.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcGovernanceRuleCatalog.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/infrastructure/JdbcAssetResponsibilityAdapter.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/infrastructure/GovernanceJdbcContractTest.java`
- Modify: `backend/src/test/java/com/tianshu/assets/governance/support/GovernanceTestFixture.java`

- [ ] **Step 1: 写失败的 JDBC 契约测试**

```java
@Test
void jdbcStoresRoundTripAFieldClosureWithoutChangingLegacySource() {
    var original = jdbcClient.sql("SELECT drawing_content FROM sys_drawing WHERE id = 104")
            .query(String.class).single();

    var completed = fixture.runCompleteJdbcClosure(104L, GovernanceField.DESCRIPTION,
            "历史设备接口图及适用说明");

    assertThat(completed.status()).isEqualTo(GovernanceTaskStatus.COMPLETED);
    assertThat(jdbcClient.sql("SELECT standard_description FROM asset_package_ext WHERE drawing_id = 104")
            .query(String.class).single()).isEqualTo("历史设备接口图及适用说明");
    assertThat(jdbcClient.sql("SELECT drawing_content FROM sys_drawing WHERE id = 104")
            .query(String.class).single()).isEqualTo(original);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceJdbcContractTest test`

Expected: FAIL 或 SKIP，V1.7 表和 JDBC 存储尚不存在；测试仅允许连接本地测试库，不读取生产配置。

- [ ] **Step 3: 编写幂等 V1.7 迁移**

迁移必须：

- 为 `asset_package_ext` 增加 `standard_description TEXT`、`standard_specialties JSON` 和标准值更新时间列；负责人写入新表 `asset_responsibility_ext`。
- 为 `asset_scope_ext` 增加 `source_type`、`governance_result_version_id` 和 `active`，保留原有行及 `source_value_json`。
- 为 `governance_task` 增加 `workflow_version`、`current_round`、`scope_snapshot_id`、`quality_policy_snapshot_id` 和 `version`，现有行默认 `LEGACY_PROGRESS`。
- 为 `governance_plan` 增加 `version`，并创建 `governance_plan_item` 连接表。
- 创建 `governance_issue`、scope snapshot/item、governance item/result、confirmation、acceptance、quality policy/snapshot、data standard/rule、operation job/job item 和 audit 表；所有状态、轮次、版本、唯一键和常用查询索引显式定义。本切片只为字段补充读取启用标准和质量策略，不增加标准管理页面。
- 使用新的 V1.7 文件，不改写 V1.5 迁移语义，不修改旧表主键和旧来源列。

配置新增 `asset.governance-schema-enabled: ${ASSET_GOVERNANCE_SCHEMA_ENABLED:false}`；`application-local.yml` 显式设为 `true`，`application-oceanbase.yml` 保持默认 `false`。只有迁移完成并经过只读核对后，OceanBase 环境才允许显式开启结构读取；`asset.database-writes-enabled` 仍是独立写门禁。

- [ ] **Step 4: 完成 JDBC 适配器的乐观锁写入**

所有更新使用 `WHERE id = :id AND version = :expectedVersion` 并原子执行 `version = version + 1`。受影响行数不是 1 时统一抛出 `GovernanceConflictException`。正式应用作业每个治理项使用独立事务；`oceanbase` profile 在 `asset.database-writes-enabled=false` 时所有命令返回只读冲突。

测试夹具增加 `runCompleteJdbcClosure(assetId, field, value)` 工厂：只接受测试 `JdbcClient` 和显式 `local` 测试 profile，通过问题、任务、执行、确认、验收和应用服务完成闭环，不读取 `.env.local` 之外的连接信息。

- [ ] **Step 5: 更新本地初始化说明和演示数据**

`docs/local-development.md` 增加按顺序执行 V1.5 bootstrap、V1.7 local migration、V1.5 seed 的命令。种子脚本使用 `INSERT IGNORE` 增加一组 `CLOSED_LOOP_V1` 草稿问题与任务，不覆盖现有三条旧任务。

- [ ] **Step 6: 在可用本地 MySQL 时运行契约测试**

Run: `cd backend && rtk mvn -Dtest=GovernanceJdbcContractTest test`

Expected: PASS；重复应用本地 V1.7 脚本无破坏性错误，闭环数据可重读，`sys_drawing` 原值保持不变。若本机未配置测试数据库，记录为未运行，不能使用生产地址替代。

- [ ] **Step 7: 提交迁移和 JDBC 实现**

```bash
git add docs/migrations docs/local-development.md backend/src/main/resources/application.yml \
  backend/src/main/resources/application-local.yml backend/src/main/resources/application-oceanbase.yml \
  backend/src/main/java/com/tianshu/assets/governance/infrastructure \
  backend/src/test/java/com/tianshu/assets/governance/infrastructure
git commit -m "feat: persist governance closed loop"
```

### Task 13: 统一闭环 API 错误、权限和 CORS 契约

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/governance/application/GovernanceConflictException.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/application/GovernanceValidationException.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/application/GovernanceNotFoundException.java`
- Create: `backend/src/main/java/com/tianshu/assets/governance/application/GovernanceAuthorizationService.java`
- Modify: `backend/src/main/java/com/tianshu/assets/common/api/ApiExceptionHandler.java`
- Modify: `backend/src/main/java/com/tianshu/assets/common/api/WebConfiguration.java`
- Test: `backend/src/test/java/com/tianshu/assets/governance/api/GovernanceClosedLoopApiTest.java`

- [ ] **Step 1: 写失败的 HTTP 契约测试**

```java
@Test
void returnsConflictForStaleItemVersion() throws Exception {
    mockMvc.perform(put("/api/v1/governance/items/501/result-draft")
            .contentType(APPLICATION_JSON)
            .content("{\"itemVersion\":0,\"assetVersion\":7,\"proposedValue\":{\"description\":\"标准说明\"},\"actorUserId\":\"emp-chen\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("governance_version_conflict"));
}

@Test
void corsAllowsPutForGovernanceCommands() throws Exception {
    mockMvc.perform(options("/api/v1/governance/items/501/result-draft")
            .header("Origin", "http://127.0.0.1:5173")
            .header("Access-Control-Request-Method", "PUT"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && rtk mvn -Dtest=GovernanceClosedLoopApiTest test`

Expected: FAIL，`PUT` 未列入 CORS，闭环异常尚未映射为稳定错误码。

- [ ] **Step 3: 增加统一异常映射**

映射规则固定为：不存在 `404 governance_not_found`；权限不足 `403 governance_forbidden`；字段或阶段门禁错误 `422 governance_validation_failed`；版本、重复领取或状态冲突 `409 governance_version_conflict`/`governance_state_conflict`；只读适配器继续返回 `409 read_only_adapter`。

- [ ] **Step 4: 加入后端权限门禁和 PUT CORS**

执行只允许计划责任人或内容管理员；确认只允许资产责任人或系统管理员；验收只允许内容管理员或系统管理员；查询无权限时不得返回资产名称、编号、原值和拟值。将 `WebConfiguration.allowedMethods` 改为 `GET, POST, PUT, PATCH, DELETE, OPTIONS`。

- [ ] **Step 5: 运行闭环 API 与完整后端套件**

Run: `cd backend && rtk mvn -Dtest=GovernanceClosedLoopApiTest test`

Expected: PASS。

Run: `cd backend && rtk mvn test`

Expected: PASS；原资产、字典、上传、协作和旧治理接口测试不回归。

- [ ] **Step 6: 提交 API 硬化**

```bash
git add backend/src/main/java/com/tianshu/assets/common \
  backend/src/main/java/com/tianshu/assets/governance \
  backend/src/test/java/com/tianshu/assets/governance
git commit -m "feat: harden governance workflow api"
```

### Task 14: 建立前端测试工具、闭环类型和 API 客户端

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/features/governance/types.ts`
- Create: `frontend/src/features/governance/api.ts`
- Create: `frontend/src/features/governance/api.test.ts`
- Delete: `frontend/src/services/governanceService.ts`

- [ ] **Step 1: 使用 pnpm 安装测试依赖**

Run: `cd frontend && rtk pnpm add -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event`

Expected: `package.json` 和 `pnpm-lock.yaml` 只增加上述开发依赖，不生成 `package-lock.json`。

- [ ] **Step 2: 配置 Vitest 并写失败的批量冲突解析测试**

```ts
// vite.config.ts
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  build: {
    chunkSizeWarningLimit: 1024,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules\/(react|react-dom|react-router)/, priority: 30 },
            { name: 'antd', test: /node_modules\/(antd|@ant-design|rc-)/, priority: 20 },
            { name: 'query', test: /node_modules\/@tanstack/, priority: 10 },
          ],
        },
      },
    },
  },
})
```

测试配置合并进现有 `defineConfig`，现有服务代理和三组构建分包配置保持不变。

```ts
it('keeps per-item batch outcomes', async () => {
  const commands: BatchResultCommand[] = [
    { itemId: 501, itemVersion: 0, assetVersion: 7, proposedValue: { description: '标准说明' }, submit: true },
    { itemId: 502, itemVersion: 0, assetVersion: 8, proposedValue: { description: '另一说明' }, submit: true },
  ]
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
    results: [
      { itemId: 501, outcome: 'SUCCESS', resultVersionId: 9001 },
      { itemId: 502, outcome: 'CONFLICT', errorCode: 'governance_version_conflict', currentVersion: 4 },
    ],
  }), { status: 200 }))

  await expect(saveBatchResults('batch-1', commands)).resolves.toMatchObject({
    results: [{ outcome: 'SUCCESS' }, { outcome: 'CONFLICT', currentVersion: 4 }],
  })
})
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `cd frontend && rtk pnpm vitest run src/features/governance/api.test.ts`

Expected: FAIL，`saveBatchResults` 和闭环类型尚不存在。

- [ ] **Step 4: 定义唯一前端契约和请求封装**

`types.ts` 明确定义 `GovernanceTaskStatus`、`GovernanceItemStatus`、`GovernanceField`、`GovernanceProgress`、`GovernanceIssue`、`GovernanceTaskDetail`、`GovernanceItem`、`GovernanceResultVersion`、`ConfirmationRound`、`AcceptanceRound`、`OperationJob` 和 `BatchItemResult`。不得使用 `any`；错误响应解析为带 `code/message/details` 的 `GovernanceApiError`。

`api.ts` 实现问题列表、按问题建单、计划保存、任务启动、治理项列表、草稿保存、单项提交、批量结果、提交确认、确认决定、验收样本、验收完成、返工、历史、报告和作业查询。

- [ ] **Step 5: 增加测试脚本并运行**

在 `package.json` 增加 `"test": "vitest run"`。

Run: `cd frontend && rtk pnpm test -- src/features/governance/api.test.ts`

Expected: PASS；逐项结果和 `409` 当前版本均保留给 UI。

- [ ] **Step 6: 提交前端契约**

```bash
git add frontend/package.json frontend/pnpm-lock.yaml frontend/vite.config.ts \
  frontend/src/test frontend/src/features/governance frontend/src/services/governanceService.ts
git commit -m "test: add governance frontend contracts"
```

### Task 15: 实现治理总览、问题池和任务详情

**Files:**
- Create: `frontend/src/features/governance/shared/GovernanceStatusTag.tsx`
- Create: `frontend/src/features/governance/shared/GovernanceProgressStrip.tsx`
- Create: `frontend/src/features/governance/overview/GovernanceOverviewPage.tsx`
- Create: `frontend/src/features/governance/issues/GovernanceIssuePoolPage.tsx`
- Create: `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx`
- Create: `frontend/src/features/governance/tasks/GovernancePlanEditor.tsx`
- Create: `frontend/src/features/governance/issues/GovernanceIssuePoolPage.test.tsx`
- Create: `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`
- Modify: `frontend/src/features/governance/GovernancePage.tsx`

- [ ] **Step 1: 写失败的问题建单和启动门禁测试**

```tsx
it('creates a task from selected issue ids without a manual total', async () => {
  renderGovernance(<GovernanceIssuePoolPage />)
  await user.click(await screen.findByRole('checkbox', { name: '选择问题 1001' }))
  await user.click(screen.getByRole('checkbox', { name: '选择问题 1002' }))
  await user.click(screen.getByRole('button', { name: '创建治理任务' }))

  expect(createTask).toHaveBeenCalledWith(expect.objectContaining({ issueIds: [1001, 1002] }))
  expect(screen.queryByLabelText('计划总量')).not.toBeInTheDocument()
})

it('shows the backend start validation without unlocking the plan', async () => {
  startTask.mockRejectedValue(new GovernanceApiError('governance_validation_failed', '治理项不能重复分配'))
  renderGovernance(<GovernanceTaskDetailPage />)
  await user.click(await screen.findByRole('button', { name: '启动任务' }))
  expect(await screen.findByText('治理项不能重复分配')).toBeVisible()
  expect(screen.getByRole('button', { name: '编辑计划' })).toBeEnabled()
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && rtk pnpm test -- src/features/governance/issues/GovernanceIssuePoolPage.test.tsx src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`

Expected: FAIL，三个页面和共享进度组件尚不存在。

- [ ] **Step 3: 实现工作型信息布局**

总览使用紧凑表格展示任务阶段、执行进度、确认覆盖、验收覆盖、阻塞/返工数量和截止日期；问题池左侧为字段/状态/资产筛选，右侧为可跨页保留的勾选表格和建单抽屉；任务详情展示范围快照、只读问题集合、计划依赖、责任人、日期和进入执行/确认/验收的明确入口。卡片仅用于重复任务项或弹窗，不把整页章节包成浮动卡片。

- [ ] **Step 4: 移除闭环任务的手工进度编辑**

`GovernancePage.tsx` 只作为 `/governance` 到 `/sys/drawing` 的兼容入口；删除“更新完成量”表单和对应 mutation。旧任务显示“历史进度，只读”标记，新闭环任务显示三个自动阶段进度。

- [ ] **Step 5: 运行页面测试、lint 和类型检查**

Run: `cd frontend && rtk pnpm test -- src/features/governance/issues/GovernanceIssuePoolPage.test.tsx src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`

Expected: PASS。

Run: `cd frontend && rtk pnpm lint && rtk pnpm typecheck`

Expected: PASS；无 `any`、未使用变量或类型错误。

- [ ] **Step 6: 提交总览和建单页面**

```bash
git add frontend/src/features/governance
git commit -m "feat: add governance issue and task views"
```

### Task 16: 实现字段清洗工作台和冲突恢复

**Files:**
- Create: `frontend/src/features/governance/execution/GovernanceExecutionPage.tsx`
- Create: `frontend/src/features/governance/execution/GovernanceItemQueue.tsx`
- Create: `frontend/src/features/governance/execution/FieldResultEditor.tsx`
- Create: `frontend/src/features/governance/execution/RuleContextPanel.tsx`
- Create: `frontend/src/features/governance/shared/BatchResultDrawer.tsx`
- Create: `frontend/src/features/governance/execution/GovernanceExecutionPage.test.tsx`

- [ ] **Step 1: 写失败的草稿、批量结果和冲突恢复测试**

```tsx
it('keeps the current item and reloads only the conflicted result', async () => {
  saveResultDraft.mockRejectedValue(new GovernanceApiError(
    'governance_version_conflict', '资产已被其他用户更新', [], { currentVersion: 4 }))
  renderGovernance(<GovernanceExecutionPage />)

  await user.type(await screen.findByLabelText('拟变更功能说明'), '标准功能说明')
  await user.click(screen.getByRole('button', { name: '保存草稿' }))

  expect(await screen.findByText('资产已被其他用户更新')).toBeVisible()
  expect(screen.getByRole('button', { name: '刷新当前项' })).toBeVisible()
  expect(screen.getByLabelText('拟变更功能说明')).toHaveValue('标准功能说明')
})

it('shows every batch outcome instead of one aggregate toast', async () => {
  renderGovernance(<GovernanceExecutionPage />)
  await user.click(await screen.findByRole('button', { name: '批量提交' }))
  expect(await screen.findByText('成功 1')).toBeVisible()
  expect(screen.getByText('冲突 1')).toBeVisible()
  expect(screen.getByText('校验失败 1')).toBeVisible()
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && rtk pnpm test -- src/features/governance/execution/GovernanceExecutionPage.test.tsx`

Expected: FAIL，执行工作台尚不存在。

- [ ] **Step 3: 实现三栏清洗工作台**

左栏固定宽度队列按 `PENDING/PROCESSING/BLOCKED/REWORK_REQUIRED/SUBMITTED` 分组；中栏使用字段专用编辑器显示原值和拟值；右栏显示标准版本、字典版本、同一范围信息、历史结果和退回意见。说明使用文本域，专业使用字典多选，负责人使用员工选择器，范围使用同一 `AssetScope` 的级联编辑器。

- [ ] **Step 4: 实现草稿保护和逐项批量结果**

切换治理项或离开路由时，如果当前值与最后保存草稿不同，显示离开确认。批量操作只有相同字段、标准版本和 `scopeFingerprint` 的选中项可用；结果抽屉逐行显示成功、校验失败、冲突和可刷新入口，不用单一成功提示掩盖失败项。

- [ ] **Step 5: 运行测试、lint 和类型检查**

Run: `cd frontend && rtk pnpm test -- src/features/governance/execution/GovernanceExecutionPage.test.tsx`

Expected: PASS。

Run: `cd frontend && rtk pnpm lint && rtk pnpm typecheck`

Expected: PASS。

- [ ] **Step 6: 提交清洗工作台**

```bash
git add frontend/src/features/governance/execution frontend/src/features/governance/shared
git commit -m "feat: add field governance workbench"
```

### Task 17: 实现确认、验收、路由和端到端闭环验证

**Files:**
- Create: `frontend/src/features/governance/confirmation/GovernanceConfirmationPage.tsx`
- Create: `frontend/src/features/governance/confirmation/ConfirmationDecisionPanel.tsx`
- Create: `frontend/src/features/governance/acceptance/GovernanceAcceptancePage.tsx`
- Create: `frontend/src/features/governance/acceptance/QualityMetricTable.tsx`
- Create: `frontend/src/features/governance/acceptance/AcceptanceSampleTable.tsx`
- Create: `frontend/src/features/governance/acceptance/ApplicationJobProgress.tsx`
- Create: `frontend/src/features/governance/confirmation/GovernanceConfirmationPage.test.tsx`
- Create: `frontend/src/features/governance/acceptance/GovernanceAcceptancePage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Modify: `requirement.md`
- Modify: `docs/technical-design.md`

- [ ] **Step 1: 写失败的确认覆盖、验收抽样和归档进度测试**

```tsx
it('requires a reason for rejection and keeps partial decisions', async () => {
  renderGovernance(<GovernanceConfirmationPage />)
  await user.click(await screen.findByRole('button', { name: '退回当前项' }))
  expect(await screen.findByText('请填写退回意见')).toBeVisible()
  await user.type(screen.getByLabelText('退回意见'), '负责人不符合实际责任范围')
  await user.click(screen.getByRole('button', { name: '保存决定' }))
  expect(screen.getByText('确认覆盖率 1 / 2')).toBeVisible()
})

it('keeps failed samples visible and polls the application job', async () => {
  renderGovernance(<GovernanceAcceptancePage />)
  expect(await screen.findByText('抽样准确率')).toBeVisible()
  expect(screen.getByText('功能说明与现场用途不符')).toBeVisible()
  await user.click(screen.getByRole('button', { name: '通过并正式应用' }))
  expect(await screen.findByText('验收通过，正在归档')).toBeVisible()
  expect(await screen.findByText('2 / 2 已应用')).toBeVisible()
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && rtk pnpm test -- src/features/governance/confirmation/GovernanceConfirmationPage.test.tsx src/features/governance/acceptance/GovernanceAcceptancePage.test.tsx`

Expected: FAIL，确认和验收页面尚不存在。

- [ ] **Step 3: 实现确认和验收工作台**

确认页逐项展示原值、拟值、规则来源和执行人；支持保存逐项决定、同责任范围批量通过、退回意见和完成轮次。验收页展示五项指标的分子/分母/阈值/适用性，固定抽样列表不可删除；退回必须选中受影响项，通过后轮询作业并显示每条失败原因和重试按钮。

- [ ] **Step 4: 配置独立路由和导航**

在 `App.tsx` 增加：

```tsx
<Route path="/sys/drawing" element={<GovernanceOverviewPage />} />
<Route path="/sys/drawing/issues" element={<GovernanceIssuePoolPage />} />
<Route path="/sys/drawing/tasks/:taskId" element={<GovernanceTaskDetailPage />} />
<Route path="/sys/drawing/tasks/:taskId/execute" element={<GovernanceExecutionPage />} />
<Route path="/sys/drawing/tasks/:taskId/confirm" element={<GovernanceConfirmationPage />} />
<Route path="/sys/drawing/tasks/:taskId/accept" element={<GovernanceAcceptancePage />} />
<Route path="/governance" element={<Navigate to="/sys/drawing" replace />} />
```

`AppShell` 的数据治理菜单保留一个主入口，并在页面内使用标签页或面包屑进入问题池；不把每个工作台都塞进全局菜单。

- [ ] **Step 5: 更新需求和技术文档的当前实现状态**

在 `requirement.md` 的数据治理流程、功能要求、字段和验收场景中明确：任务按问题建单、进度由治理项聚合、确认后进入验收、验收通过后异步正式应用、失败进入返工轮次。更新 `docs/technical-design.md` 的 API 契约、模块边界、V1.7 迁移和 profile 写入约束；后续映射、文件治理和复杂治理仍标为后续切片，不声称已完成。

- [ ] **Step 6: 运行全部自动验证**

Run: `cd frontend && rtk pnpm test`

Expected: PASS。

Run: `cd frontend && rtk pnpm lint && rtk pnpm typecheck && rtk pnpm build`

Expected: PASS。

Run: `cd backend && rtk mvn test`

Expected: PASS。

- [ ] **Step 7: 启动本地服务并提供浏览器证据**

在默认 `dev` profile 启动后端，使用未占用端口启动前端。验证 1366x768 和 1920x1080：问题池建单、计划启动、字段草稿和提交、确认部分退回、返工再提交、验收通过、作业完成和报告查看。截图必须证明页面无重叠、无横向溢出，队列、当前项和主要命令同时可见；同时确认默认 profile 未连接数据库。

- [ ] **Step 8: 检查相关 diff 并提交完整闭环**

Run: `rtk git diff --check`

Expected: PASS，无空白错误。

Run: `rtk git status --short`

Expected: 只包含本计划范围内的治理、迁移和文档文件；任何用户已有无关改动保持原样。

```bash
git add frontend/src/features/governance/confirmation \
  frontend/src/features/governance/acceptance \
  frontend/src/App.tsx frontend/src/app/AppShell.tsx \
  requirement.md docs/technical-design.md
git commit -m "feat: complete field governance closed loop"
```

## Final Verification Checklist

- [ ] 新闭环任务只能从开放问题 ID 创建，数量由治理项派生。
- [ ] 启动时固化问题、资产、规则、标准、质量策略和范围版本。
- [ ] 每个治理项恰好属于一个计划，依赖同任务、非自身且无环。
- [ ] 草稿、提交、确认、验收、返工和正式应用都有真实状态与持久化记录。
- [ ] `BLOCKED` 和 `REWORK_REQUIRED` 保留在分母中，前端单独展示。
- [ ] 拟变更值在验收通过前不会进入资产正式扩展值。
- [ ] 正式应用逐项幂等，部分失败保留成功项且可恢复。
- [ ] 资产还有其他阻断问题时保持 `PENDING_CURATION`，任务仍可在当前范围完成。
- [ ] 旧任务标记 `LEGACY_PROGRESS` 并只读展示，不伪造结果版本。
- [ ] `dev` profile 完整运行内存闭环；`local` 可持久化；`oceanbase` 默认拒绝写入。
- [ ] 旧主键、`sys_drawing` 来源字段、历史文件和协作关系均未被覆盖或删除。
- [ ] 产品和生产条件始终在同一 `AssetScope` 中校验。
- [ ] 批量接口和页面逐项显示成功、校验失败、冲突和重试结果。
- [ ] 后端、前端测试、lint、typecheck、build 和桌面浏览器验证均有证据。
