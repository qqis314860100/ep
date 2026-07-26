package com.tianshu.assets.governance.execution.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceExecutionService {

    private final GovernanceExecutionStore executionStore;
    private final GovernanceWorkflowStore workflowStore;
    private final GovernanceRuleCatalog ruleCatalog;
    private final AssetRepository assetRepository;
    private final GovernanceActionHandler actionHandler;
    private final Clock clock;

    public GovernanceExecutionService(
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            AssetRepository assetRepository,
            GovernanceActionHandler actionHandler) {
        this(executionStore, workflowStore, ruleCatalog, assetRepository, actionHandler, Clock.systemUTC());
    }

    public GovernanceExecutionService(
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            AssetRepository assetRepository,
            GovernanceActionHandler actionHandler,
            Clock clock) {
        this.executionStore = executionStore;
        this.workflowStore = workflowStore;
        this.ruleCatalog = ruleCatalog;
        this.assetRepository = assetRepository;
        this.actionHandler = actionHandler;
        this.clock = clock;
    }

    public List<ItemExecutionContext> items(long taskId) {
        var scopeSnapshot = workflowStore.scopeSnapshotForTask(taskId);
        var scopeItems = workflowStore.scopeItemsForTask(taskId);
        return executionStore.items(taskId).stream().map(item -> {
            var frozen = scopeItems.stream()
                    .filter(candidate -> candidate.issueId() == item.issueId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("治理项缺少冻结事实"));
            return new ItemExecutionContext(
                    item, executionStore.currentResult(item.id()), frozen.originalFactJson(),
                    scopeSnapshot.ruleSnapshot(), item.blockReason(), item.reworkSourceItemId());
        }).toList();
    }

    @Transactional
    public GovernanceResultVersion saveDraft(long itemId, SaveResultDraftCommand command) {
        validateCommand(command);
        var item = executionStore.item(itemId);
        if (item.version() != command.itemVersion()) {
            throw new GovernanceVersionConflictException("治理项已变化，请刷新后重试");
        }
        if (item.assetVersion() != command.assetVersion()) {
            throw new GovernanceVersionConflictException("资产版本已变化，请刷新后重试");
        }
        if (item.status() == GovernanceItemStatus.SUBMITTED) {
            throw new GovernanceConflictException("已提交结果不可原地修改");
        }
        if (item.status() != GovernanceItemStatus.PENDING && item.status() != GovernanceItemStatus.PROCESSING) {
            throw new GovernanceConflictException("当前治理项状态不能保存草稿");
        }
        var snapshot = workflowStore.scopeSnapshotForTask(item.taskId());
        var frozenItem = workflowStore.scopeItemsForTask(item.taskId()).stream()
                .filter(candidate -> candidate.issueId() == item.issueId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("治理项缺少冻结事实"));
        assetRepository.findById(item.assetId())
                .orElseThrow(() -> new IllegalArgumentException("治理资产不存在"));
        var enabledRules = ruleCatalog.enabledSnapshot();
        actionHandler.validate(
                item.targetField(), command.proposedValueJson(),
                new GovernanceActionHandler.ValidationContext(
                        snapshot.ruleSnapshot(), enabledRules, ruleCatalog.validScopes()));
        return executionStore.saveDraft(new GovernanceExecutionStore.SaveDraft(
                item.id(), command.itemVersion(), command.assetVersion(), item.targetField(),
                frozenItem.originalFactJson(), command.proposedValueJson(),
                snapshot.ruleSnapshot().dataStandardVersion(), snapshot.ruleSnapshot().dictionaryVersions(),
                command.actorUserId(), Instant.now(clock)));
    }

    @Transactional
    public GovernanceResultVersion submit(
            long itemId, long resultVersionId, long resultVersion, String actorUserId) {
        if (resultVersionId <= 0 || resultVersion < 0) {
            throw new GovernanceValidationException("治理结果版本不合法");
        }
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new GovernanceValidationException("操作人不能为空");
        }
        return executionStore.submit(new GovernanceExecutionStore.Submit(
                itemId, resultVersionId, resultVersion, actorUserId, Instant.now(clock)));
    }

    private void validateCommand(SaveResultDraftCommand command) {
        if (command.itemVersion() < 0 || command.assetVersion() < 0) {
            throw new GovernanceValidationException("治理项或资产版本不合法");
        }
        if (command.proposedValueJson() == null || command.actorUserId() == null
                || command.actorUserId().isBlank()) {
            throw new GovernanceValidationException("治理结果和操作人不能为空");
        }
    }

    public record SaveResultDraftCommand(
            long itemVersion,
            long assetVersion,
            String proposedValueJson,
            String actorUserId) {}

    public record ItemExecutionContext(
            GovernanceItem item,
            GovernanceResultVersion currentResult,
            String originalFactJson,
            GovernanceRuleSnapshot ruleSnapshot,
            String blockReason,
            Long reworkSourceItemId) {}
}
