package com.tianshu.assets.governance.execution.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GovernanceExecutionService {

    private final GovernanceExecutionStore executionStore;
    private final GovernanceWorkflowStore workflowStore;
    private final GovernanceRuleCatalog ruleCatalog;
    private final AssetRepository assetRepository;
    private final GovernanceActionHandler actionHandler;
    private final Clock clock;
    private final TransactionTemplate itemTransaction;
    private final Map<String, SavedBatch> batches = new LinkedHashMap<>();

    @Autowired
    public GovernanceExecutionService(
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            AssetRepository assetRepository,
            GovernanceActionHandler actionHandler,
            ObjectProvider<PlatformTransactionManager> transactionManager) {
        this(executionStore, workflowStore, ruleCatalog, assetRepository, actionHandler,
                Clock.systemUTC(), transactionManager.getIfAvailable());
    }

    public GovernanceExecutionService(
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            AssetRepository assetRepository,
            GovernanceActionHandler actionHandler,
            Clock clock) {
        this(executionStore, workflowStore, ruleCatalog, assetRepository, actionHandler, clock, null);
    }

    private GovernanceExecutionService(
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            AssetRepository assetRepository,
            GovernanceActionHandler actionHandler,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.executionStore = executionStore;
        this.workflowStore = workflowStore;
        this.ruleCatalog = ruleCatalog;
        this.assetRepository = assetRepository;
        this.actionHandler = actionHandler;
        this.clock = clock;
        itemTransaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (itemTransaction != null) {
            itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
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

    public synchronized BatchExecutionResult batchResults(
            String idempotencyKey, List<BatchResultCommand> commands) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new GovernanceValidationException("批量幂等键不能为空");
        }
        if (commands == null || commands.isEmpty()) {
            throw new GovernanceValidationException("批量治理结果不能为空");
        }
        var digest = digest(commands);
        var saved = batches.get(idempotencyKey);
        if (saved != null) {
            if (!saved.digest().equals(digest)) {
                throw new GovernanceConflictException("幂等键已用于不同批量请求");
            }
            return saved.result();
        }

        var baseline = commands.stream().filter(Objects::nonNull).findFirst().orElse(null);
        var results = commands.stream()
                .map(command -> executeBatchItem(command, baseline))
                .toList();
        var response = new BatchExecutionResult(idempotencyKey, digest, results);
        batches.put(idempotencyKey, new SavedBatch(digest, response));
        return response;
    }

    private BatchItemResult executeBatchItem(BatchResultCommand command, BatchResultCommand baseline) {
        if (command == null) {
            return new BatchItemResult(0, BatchItemResult.BatchOutcome.VALIDATION_FAILED, null,
                    "validation_failed", "批量治理命令不能为空", null);
        }
        if (command.targetField() != baseline.targetField()
                || command.standardVersion() != baseline.standardVersion()
                || !Objects.equals(command.scopeFingerprint(), baseline.scopeFingerprint())) {
            return new BatchItemResult(command.itemId(), BatchItemResult.BatchOutcome.VALIDATION_FAILED, null,
                    "batch_constraint", "批量治理项必须使用相同目标字段、标准版本和适用范围", null);
        }
        GovernanceItem item;
        try {
            item = executionStore.item(command.itemId());
        } catch (IllegalArgumentException exception) {
            return new BatchItemResult(command.itemId(), BatchItemResult.BatchOutcome.VALIDATION_FAILED, null,
                    "item_not_found", "治理项不存在", null);
        }
        try {
            return itemTransaction == null
                    ? executeBatchItemInTransaction(command, item)
                    : Objects.requireNonNull(
                            itemTransaction.execute(status -> executeBatchItemInTransaction(command, item)),
                            "逐项治理事务未返回结果");
        } catch (GovernanceValidationException exception) {
            return failure(command.itemId(), BatchItemResult.BatchOutcome.VALIDATION_FAILED,
                    "validation_failed", exception.getMessage());
        } catch (GovernanceVersionConflictException exception) {
            return failure(command.itemId(), BatchItemResult.BatchOutcome.CONFLICT,
                    "version_conflict", exception.getMessage());
        } catch (GovernanceConflictException exception) {
            return failure(command.itemId(), BatchItemResult.BatchOutcome.CONFLICT,
                    "conflict", exception.getMessage());
        }
    }

    private BatchItemResult executeBatchItemInTransaction(
            BatchResultCommand command, GovernanceItem item) {
        var snapshot = workflowStore.scopeSnapshotForTask(item.taskId());
        if (item.targetField() != command.targetField()
                || snapshot.ruleSnapshot().dataStandardVersion() != command.standardVersion()
                || !Objects.equals(item.scopeFingerprint(), command.scopeFingerprint())) {
            return new BatchItemResult(command.itemId(), BatchItemResult.BatchOutcome.VALIDATION_FAILED, null,
                    "batch_constraint", "批量治理项约束与冻结范围不一致", item.version());
        }
        var draft = saveDraft(command.itemId(), command.toSaveCommand());
        var submitted = submit(command.itemId(), draft.id(), draft.version(), command.actorUserId());
        return new BatchItemResult(command.itemId(), BatchItemResult.BatchOutcome.SUCCESS,
                submitted.id(), null, null, executionStore.item(command.itemId()).version());
    }

    private BatchItemResult failure(
            long itemId, BatchItemResult.BatchOutcome outcome, String errorCode, String message) {
        Long currentVersion = null;
        try {
            currentVersion = executionStore.item(itemId).version();
        } catch (IllegalArgumentException ignored) {
            // Item-level failures remain isolated from the rest of the batch.
        }
        return new BatchItemResult(itemId, outcome, null, errorCode, message, currentVersion);
    }

    private String digest(List<BatchResultCommand> commands) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            commands.forEach(command -> digest.update(String.valueOf(command).getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
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

    public record BatchResultCommand(
            long itemId,
            long itemVersion,
            long assetVersion,
            GovernanceField targetField,
            long standardVersion,
            String scopeFingerprint,
            String proposedValueJson,
            String actorUserId) {
        public SaveResultDraftCommand toSaveCommand() {
            return new SaveResultDraftCommand(itemVersion, assetVersion, proposedValueJson, actorUserId);
        }
    }

    public record BatchExecutionResult(
            String idempotencyKey, String requestDigest, List<BatchItemResult> results) {
        public BatchExecutionResult {
            results = List.copyOf(results);
        }
    }

    private record SavedBatch(String digest, BatchExecutionResult result) {}

    public record ItemExecutionContext(
            GovernanceItem item,
            GovernanceResultVersion currentResult,
            String originalFactJson,
            GovernanceRuleSnapshot ruleSnapshot,
            String blockReason,
            Long reworkSourceItemId) {}
}
