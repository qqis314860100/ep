package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityPolicySnapshot;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GovernanceAcceptancePreparationService {

    private final GovernanceQualityService qualityService;
    private final GovernanceTaskStore taskStore;
    private final GovernanceExecutionStore executionStore;
    private final GovernanceWorkflowStore workflowStore;
    private final AssetRepository assetRepository;
    private final AssetResponsibilityPort responsibilityPort;

    public GovernanceAcceptancePreparationService(
            GovernanceQualityService qualityService,
            GovernanceTaskStore taskStore,
            GovernanceExecutionStore executionStore,
            GovernanceWorkflowStore workflowStore,
            AssetRepository assetRepository,
            AssetResponsibilityPort responsibilityPort) {
        this.qualityService = qualityService;
        this.taskStore = taskStore;
        this.executionStore = executionStore;
        this.workflowStore = workflowStore;
        this.assetRepository = assetRepository;
        this.responsibilityPort = responsibilityPort;
    }

    public GovernanceAcceptanceRound currentOrOpen(long taskId) {
        try {
            return qualityService.currentRound(taskId);
        } catch (com.tianshu.assets.governance.application.GovernanceConflictException ignored) {
            var task = taskStore.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
            if (task.status() != GovernanceTaskStatus.PENDING_ACCEPTANCE) throw ignored;
            var rule = workflowStore.scopeSnapshotForTask(taskId).ruleSnapshot();
            var policy = new GovernanceQualityPolicySnapshot(
                    rule.id(), rule.qualityPolicyId(), rule.qualityPolicyVersion(),
                    Arrays.stream(GovernanceQualityMetric.values()).collect(
                            Collectors.toMap(metric -> metric, metric -> 0.8)),
                    true, true, 1);
            var facts = executionStore.items(taskId).stream().map(item -> {
                var asset = assetRepository.findById(item.assetId())
                        .orElseThrow(() -> new IllegalArgumentException("治理资产不存在"));
                var scopeValid = asset.scopes().stream().anyMatch(scope ->
                        !scope.platformFamily().isBlank() && !scope.productLine().isBlank()
                                && !scope.base().isBlank() && !scope.productionLine().isBlank());
                return new GovernanceQualityService.QualityFact(
                        item.id(), executionStore.currentResult(item.id()) != null, scopeValid,
                        item.targetField() == GovernanceField.SPECIALTIES ? Boolean.TRUE : null,
                        responsibilityPort.currentResponsibility(item.assetId()).isPresent());
            }).toList();
            return qualityService.openRound(taskId, task.currentRound(), policy, facts);
        }
    }
}
