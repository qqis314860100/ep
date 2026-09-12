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

    /**
     * 治理域读操作闸门：只要求登录身份，不限角色、不做数据范围过滤。
     *
     * <p>为什么不是「管理员才能读」：治理台首页对非管理员同样开放，前端按角色只展示本人的任务
     * （见 {@code GovernanceRailHome}），把读锁到管理员会把责任人的工作台关掉。数据范围过滤
     * 仍归 S7；这里只关掉「匿名可读治理数据」。
     *
     * <p>fail-closed：{@code userId} 为空即拒绝。
     */
    public void requireAuthenticated(String userId) {
        if (userId == null || userId.isBlank()) forbidden();
    }

    public void requireAcceptance(String userId, String roles) {
        requireGovernanceAdmin(userId, roles);
    }

    /**
     * 治理配置侧写操作闸门：必须是登录用户，且具备治理管理员角色（内容管理员或系统管理员）。
     *
     * <p><b>fail-closed 是刻意的</b>：{@code SessionIdentityFilter} 只对真实 HTTP 请求生效，
     * 单测走 {@code MockMvcBuilders.standaloneSetup} 时过滤器不参与。若这里只看角色，
     * 「忘了传身份头」会静默变成放行——这正是 D-002 的成因（短构造器 + {@code != null} 判断
     * 让授权整条链路可以被绕过而不报错）。
     */
    public void requireGovernanceAdmin(String userId, String roles) {
        requireAuthenticated(userId);
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
