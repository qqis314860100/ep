package com.tianshu.assets.governance.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.infrastructure.InMemoryAssetResponsibilityAdapter;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceExecutionStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceWorkflowStore;
import org.junit.jupiter.api.Test;

/**
 * 治理授权闸门的 fail-closed 语义（D-002 的回归测试）。
 *
 * <p>D-002 的形态不是「判错了」，而是「压根没判」：controller 曾有不带授权服务的短构造器，
 * 配合 7 处 {@code if (authorizationService != null)}，任一环节缺失就静默放行。
 * 短构造器已删；这里钉住第二道保险——<b>身份缺失一律拒绝</b>，而不是只测正常路径。
 *
 * <p>注意每个断言都在「角色看起来合法」的前提下要求拒绝：只看角色的实现会在这里放行。
 */
class GovernanceAuthorizationServiceTest {

    private final GovernanceAuthorizationService authorization = new GovernanceAuthorizationService(
            new InMemoryGovernanceExecutionStore(new InMemoryGovernanceWorkflowStore()),
            new InMemoryAssetResponsibilityAdapter());

    @Test
    void governanceAdminRejectsMissingIdentityEvenWhenRolesLookElevated() {
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin("", "CONTENT_ADMIN,SYSTEM_ADMIN"))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin(null, "SYSTEM_ADMIN"))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin("   ", "CONTENT_ADMIN"))
                .isInstanceOf(GovernanceAuthorizationException.class);
    }

    @Test
    void governanceAdminRejectsIdentityWithoutAdminRole() {
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin("emp-chen", "NORMAL_USER"))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin("emp-chen", ""))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireGovernanceAdmin(
                "emp-chen", "UPLOADER,DOCUMENT_MAINTAINER"))
                .isInstanceOf(GovernanceAuthorizationException.class);
    }

    @Test
    void governanceAdminAcceptsEitherAdminRole() {
        assertThatCode(() -> authorization.requireGovernanceAdmin("admin", "CONTENT_ADMIN"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorization.requireGovernanceAdmin("admin", "SYSTEM_ADMIN"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorization.requireGovernanceAdmin("admin", "NORMAL_USER,CONTENT_ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void readGateRequiresLoginButNotAdminRole() {
        // 治理台首页对普通员工开放（前端按角色只展示本人任务），故读闸门不卡角色；
        // 数据范围过滤归 S7，这里只关掉「匿名可读治理数据」。
        assertThatCode(() -> authorization.requireAuthenticated("emp-chen"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireAuthenticated(""))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireAuthenticated(null))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireAuthenticated("  "))
                .isInstanceOf(GovernanceAuthorizationException.class);
    }

    @Test
    void acceptanceGateIsAtLeastAsStrictAsGovernanceAdmin() {
        assertThatThrownBy(() -> authorization.requireAcceptance("", "CONTENT_ADMIN"))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireAcceptance("emp-chen", "NORMAL_USER"))
                .isInstanceOf(GovernanceAuthorizationException.class);
        assertThatCode(() -> authorization.requireAcceptance("admin", "SYSTEM_ADMIN"))
                .doesNotThrowAnyException();
    }
}
