package com.tianshu.assets.governance.issue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService.CreateGovernanceTaskCommand;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceIssueServiceTest {

    private InMemoryGovernanceIssueStore issueStore;
    private InMemoryGovernanceTaskStore taskStore;
    private GovernanceIssueService service;

    @BeforeEach
    void setUp() {
        issueStore = InMemoryGovernanceIssueStore.withFieldSeeds();
        taskStore = new InMemoryGovernanceTaskStore();
        service = new GovernanceIssueService(issueStore, taskStore);
    }

    @Test
    void createsTaskFromOpenIssuesAndDerivesTotalFromIssueCount() {
        var task = service.createTask(command(List.of(1001L, 1002L)));

        assertThat(task.actionType()).isEqualTo("FIELD_SUPPLEMENT");
        assertThat(task.issueType()).isEqualTo("FIELD_COMPLETENESS");
        assertThat(task.ownerUserId()).isEqualTo("emp-chen");
        assertThat(task.ownerName()).isEqualTo("陈工");
        assertThat(task.assigneeId()).isEqualTo("emp-chen");
        assertThat(task.dueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(task.status()).isEqualTo(GovernanceTaskStatus.DRAFT);
        assertThat(task.currentRound()).isEqualTo(1);
        assertThat(task.workflowVersion()).isEqualTo(GovernanceWorkflowVersion.CLOSED_LOOP_V1);
        assertThat(task.scopeSnapshotId()).isNull();
        assertThat(task.qualityPolicySnapshotId()).isNull();
        assertThat(task.legacyTotal()).isZero();
        assertThat(task.legacyCompleted()).isZero();
        assertThat(task.version()).isZero();
        assertThat(issueStore.findClaimedByTask(task.id())).hasSize(2);
    }

    @Test
    void refusesClaimingTheSameIssueTwice() {
        var command = command(List.of(1001L));
        service.createTask(command);

        assertThatThrownBy(() -> service.createTask(command))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("问题已被其他治理任务纳入");
        assertThat(taskStore.findAll()).hasSize(1);
    }

    @Test
    void rejectsEmptyIssueIds() {
        assertThatThrownBy(() -> service.createTask(command(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("问题 ID 不能为空");
    }

    @Test
    void rejectsDuplicateIssueIds() {
        assertThatThrownBy(() -> service.createTask(command(List.of(1001L, 1001L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("问题 ID 不能重复");
    }

    @Test
    void rejectsMissingIssueBeforeTaskInsertion() {
        assertThatThrownBy(() -> service.createTask(command(List.of(1001L, 9999L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("治理问题不存在");

        assertThat(taskStore.findAll()).isEmpty();
        assertThat(issueStore.findByIds(List.of(1001L)).getFirst().status())
                .isEqualTo(GovernanceIssueStatus.OPEN);
    }

    @Test
    void claimIsAllOrNothingWhenOneIssueIsAlreadyClaimed() {
        service.createTask(command(List.of(1002L)));

        assertThatThrownBy(() -> service.createTask(command(List.of(1001L, 1002L))))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("问题已被其他治理任务纳入");

        assertThat(taskStore.findAll()).hasSize(1);
        assertThat(issueStore.findByIds(List.of(1001L)).getFirst().status())
                .isEqualTo(GovernanceIssueStatus.OPEN);
    }

    @Test
    void rejectsDuplicateFingerprintWithoutPartialInsertion() {
        var duplicate = new GovernanceIssue(
                0, 101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description",
                "FIELD_REQUIRED", 1, "{}", 3, "", "HIGH", true,
                GovernanceIssueStatus.OPEN, null, 0);

        assertThatThrownBy(() -> issueStore.insertAll(List.of(
                        issue(2001, 105, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId"),
                        duplicate)))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("治理问题已存在");
        assertThat(issueStore.findByIds(List.of(2001L))).isEmpty();
    }

    @Test
    void filtersIssuesWithoutExposingMutableCollections() {
        var descriptions = service.list(GovernanceField.DESCRIPTION, GovernanceIssueStatus.OPEN, null);
        var assetIssues = service.list(null, null, 101L);

        assertThat(descriptions).allMatch(issue -> issue.targetField() == GovernanceField.DESCRIPTION);
        assertThat(assetIssues).allMatch(issue -> issue.assetId() == 101L);
        assertThatThrownBy(() -> descriptions.add(issue(
                        3001, 105, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CreateGovernanceTaskCommand command(List<Long> issueIds) {
        return new CreateGovernanceTaskCommand(
                "历史字段补充", issueIds, "emp-chen", "陈工", LocalDate.of(2026, 9, 1));
    }

    private GovernanceIssue issue(
            long id, long assetId, GovernanceField field, String issueType, String targetPath) {
        return new GovernanceIssue(
                id, assetId, field, issueType, targetPath, "FIELD_REQUIRED", 1,
                "{}", 1, "", "MEDIUM", false, GovernanceIssueStatus.OPEN, null, 0);
    }
}
