package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore.OpenRework;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceReworkService {

    private final GovernanceTaskStore taskStore;
    private final GovernanceExecutionStore executionStore;
    private final Clock clock;

    @Autowired
    public GovernanceReworkService(
            GovernanceTaskStore taskStore, GovernanceExecutionStore executionStore) {
        this(taskStore, executionStore, Clock.systemUTC());
    }

    public GovernanceReworkService(
            GovernanceTaskStore taskStore, GovernanceExecutionStore executionStore, Clock clock) {
        this.taskStore = taskStore;
        this.executionStore = executionStore;
        this.clock = clock;
    }

    @Transactional
    public GovernanceTask open(
            long taskId, long expectedTaskVersion, String reason, String actorUserId) {
        if (reason == null || reason.isBlank() || actorUserId == null || actorUserId.isBlank()) {
            throw new GovernanceValidationException("返工说明和操作人不能为空");
        }
        synchronized (taskStore) {
            var task = taskStore.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
            if (task.version() != expectedTaskVersion) {
                throw new GovernanceVersionConflictException("治理任务已变化，请刷新后重试");
            }
            if (task.status() != GovernanceTaskStatus.REWORK_REQUIRED) {
                throw new GovernanceTaskStateException("治理任务不在待返工状态");
            }
            var items = executionStore.items(taskId);
            var reworkItems = items.stream()
                    .filter(item -> item.status() == GovernanceItemStatus.REWORK_REQUIRED)
                    .toList();
            if (reworkItems.isEmpty()) {
                throw new GovernanceValidationException("治理任务没有待返工项");
            }
            if (items.stream().anyMatch(item -> item.status() != GovernanceItemStatus.REWORK_REQUIRED
                    && item.status() != GovernanceItemStatus.CONFIRMED)) {
                throw new GovernanceValidationException("治理任务包含不可开启返工的治理项");
            }
            var nextRound = task.currentRound() + 1;
            var openedAt = Instant.now(clock);
            reworkItems.forEach(item -> executionStore.openRework(new OpenRework(
                    item.id(), item.version(), nextRound, reason, actorUserId, openedAt)));
            return taskStore.update(new GovernanceTask(
                    task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                    task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(),
                    task.status().moveTo(GovernanceTaskStatus.IN_PROGRESS), nextRound,
                    task.workflowVersion(), task.scopeSnapshotId(), task.qualityPolicySnapshotId(),
                    task.legacyTotal(), task.legacyCompleted(), task.version()), expectedTaskVersion);
        }
    }
}
