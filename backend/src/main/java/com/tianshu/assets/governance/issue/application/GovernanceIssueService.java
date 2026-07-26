package com.tianshu.assets.governance.issue.application;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceIssueService {

    private final GovernanceIssueStore issueStore;
    private final GovernanceTaskStore taskStore;

    public GovernanceIssueService(GovernanceIssueStore issueStore, GovernanceTaskStore taskStore) {
        this.issueStore = issueStore;
        this.taskStore = taskStore;
    }

    public List<GovernanceIssue> list(GovernanceField field, GovernanceIssueStatus status, Long assetId) {
        return issueStore.find(field, status, assetId);
    }

    @Transactional
    public GovernanceTask createTask(CreateGovernanceTaskCommand command) {
        validate(command);
        synchronized (issueStore) {
            var issues = issueStore.findByIds(command.issueIds());
            if (issues.size() != command.issueIds().size()) {
                throw new IllegalArgumentException("治理问题不存在");
            }
            if (issues.stream().anyMatch(issue -> issue.status() != GovernanceIssueStatus.OPEN)) {
                throw new GovernanceConflictException("问题已被其他治理任务纳入");
            }

            var task = taskStore.insert(new GovernanceTask(
                    0, taskNumber(), command.name(), "FIELD_SUPPLEMENT", "FIELD_COMPLETENESS",
                    command.ownerUserId(), command.ownerName(), command.ownerUserId(), command.dueDate(),
                    GovernanceTaskStatus.DRAFT, 1, GovernanceWorkflowVersion.CLOSED_LOOP_V1,
                    null, null, 0, 0, 0));
            issueStore.claimOpen(issues, task.id());
            return task;
        }
    }

    private void validate(CreateGovernanceTaskCommand command) {
        if (command == null) throw new IllegalArgumentException("建单命令不能为空");
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("治理任务名称不能为空");
        }
        if (command.issueIds() == null || command.issueIds().isEmpty()) {
            throw new IllegalArgumentException("问题 ID 不能为空");
        }
        if (command.issueIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("问题 ID 不合法");
        }
        if (new HashSet<>(command.issueIds()).size() != command.issueIds().size()) {
            throw new IllegalArgumentException("问题 ID 不能重复");
        }
        if (command.ownerUserId() == null || command.ownerUserId().isBlank()
                || command.ownerName() == null || command.ownerName().isBlank()) {
            throw new IllegalArgumentException("治理任务负责人不能为空");
        }
        if (command.dueDate() == null) throw new IllegalArgumentException("计划完成日期不能为空");
    }

    private String taskNumber() {
        return "GOV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public record CreateGovernanceTaskCommand(
            String name,
            List<Long> issueIds,
            String ownerUserId,
            String ownerName,
            LocalDate dueDate) {}
}
