package com.tianshu.assets.governance.execution.domain;

import com.tianshu.assets.governance.issue.domain.GovernanceField;
import java.time.Instant;
import java.util.Map;

public record GovernanceResultVersion(
        long id,
        long itemId,
        int governanceRound,
        int resultVersion,
        GovernanceField field,
        String originalValueJson,
        String proposedValueJson,
        long standardVersion,
        Map<String, Long> dictionaryVersions,
        GovernanceResultStatus status,
        String actorUserId,
        Instant savedAt,
        Instant submittedAt,
        long version) {

    public GovernanceResultVersion {
        if (field == null || status == null) throw new IllegalArgumentException("治理结果类型和状态不能为空");
        if (originalValueJson == null || proposedValueJson == null) {
            throw new IllegalArgumentException("治理结果原值和建议值不能为空");
        }
        if (standardVersion <= 0 || governanceRound <= 0 || resultVersion <= 0 || version < 0) {
            throw new IllegalArgumentException("治理结果版本不合法");
        }
        dictionaryVersions = dictionaryVersions == null ? Map.of() : Map.copyOf(dictionaryVersions);
        if (actorUserId == null || actorUserId.isBlank() || savedAt == null) {
            throw new IllegalArgumentException("治理结果操作信息不能为空");
        }
    }
}
