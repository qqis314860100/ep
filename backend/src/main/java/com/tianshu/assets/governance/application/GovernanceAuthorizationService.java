package com.tianshu.assets.governance.application;

import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceAuthorizationService {

    private static final String CONTENT_ADMIN = "CONTENT_ADMIN";
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final GovernanceExecutionStore executionStore;
    private final AssetResponsibilityPort responsibilityPort;

    public GovernanceAuthorizationService(GovernanceExecutionStore executionStore) {
        this(executionStore, null);
    }

    @Autowired
    public GovernanceAuthorizationService(
            GovernanceExecutionStore executionStore,
            AssetResponsibilityPort responsibilityPort) {
        this.executionStore = executionStore;
        this.responsibilityPort = responsibilityPort;
    }

    public void requireExecution(long itemId, String userId, String roles) {
        var item = requireItem(itemId);
        if (!item.responsibleUserId().equals(userId) && !hasRole(roles, CONTENT_ADMIN)) {
            forbidden();
        }
    }

    public void requireExecutionTask(long taskId, String userId, String roles) {
        if (hasRole(roles, CONTENT_ADMIN)) return;
        var items = executionStore.items(taskId);
        if (items.isEmpty()) throw new GovernanceNotFoundException("治理任务不存在或尚未生成治理项");
        if (items.stream().anyMatch(item -> !item.responsibleUserId().equals(userId))) forbidden();
    }

    public void requireConfirmation(long itemId, String userId, String roles) {
        if (hasRole(roles, SYSTEM_ADMIN)) return;
        if (responsibilityPort == null) forbidden();
        var item = requireItem(itemId);
        var responsibility = responsibilityPort.currentResponsibility(item.assetId())
                .orElseThrow(() -> new GovernanceNotFoundException("资产责任关系不存在"));
        if (!responsibility.responsibleUserId().equals(userId)) forbidden();
    }

    public void requireConfirmationTask(long taskId, String userId, String roles) {
        if (hasRole(roles, SYSTEM_ADMIN)) return;
        var items = executionStore.items(taskId);
        if (items.isEmpty()) throw new GovernanceNotFoundException("治理任务不存在或尚未生成治理项");
        items.forEach(item -> requireConfirmation(item.id(), userId, roles));
    }

    public void requireAcceptance(String roles) {
        if (!hasRole(roles, CONTENT_ADMIN) && !hasRole(roles, SYSTEM_ADMIN)) forbidden();
    }

    private GovernanceItem requireItem(long itemId) {
        try {
            return executionStore.item(itemId);
        } catch (IllegalArgumentException exception) {
            throw new GovernanceNotFoundException("治理项不存在");
        }
    }

    private boolean hasRole(String roles, String expected) {
        if (roles == null || roles.isBlank()) return false;
        Set<String> normalized = Arrays.stream(roles.split(","))
                .map(String::trim).filter(role -> !role.isEmpty()).collect(Collectors.toSet());
        return normalized.contains(expected);
    }

    private void forbidden() {
        throw new GovernanceAuthorizationException("无权访问或操作该治理数据");
    }
}
