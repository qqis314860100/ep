package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import org.junit.jupiter.api.Test;

class InMemoryGovernanceAssetAdapterTest {

    /** 扫描盖章的资产版本是 updatedAt 毫秒时间戳；全新适配器首次正式应用应以此版本为基线，而不是用默认版本 0 冲突。 */
    @Test
    void appliesScanStampedVersionOnFirstApply() {
        var adapter = new InMemoryGovernanceAssetAdapter();
        long stampedVersion = 1_758_123_456_789L;

        var outcome = adapter.applyFieldResult(
                1, 106, GovernanceField.SCOPE, "{\"scopes\":[]}", stampedVersion, "emp-admin");

        assertThat(outcome.assetVersion()).isEqualTo(stampedVersion + 1);
        assertThat(adapter.snapshot(106).version()).isEqualTo(stampedVersion + 1);
    }

    /** 已显式播种（如测试夹具）的状态仍执行严格乐观锁校验，版本不一致必须报错。 */
    @Test
    void keepsStrictVersionCheckForSeededState() {
        var adapter = new InMemoryGovernanceAssetAdapter();
        adapter.seed(106, 0);

        assertThatThrownBy(() -> adapter.applyFieldResult(
                1, 106, GovernanceField.SCOPE, "{\"scopes\":[]}", 5, "emp-admin"))
                .isInstanceOf(GovernanceVersionConflictException.class);
    }
}
