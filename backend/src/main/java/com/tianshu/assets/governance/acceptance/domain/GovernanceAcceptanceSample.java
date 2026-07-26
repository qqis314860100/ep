package com.tianshu.assets.governance.acceptance.domain;

import java.time.Instant;

public record GovernanceAcceptanceSample(
        long id,
        long roundId,
        long itemId,
        Boolean passed,
        String issueDescription,
        String reviewerUserId,
        Instant checkedAt,
        long version) {

    public GovernanceAcceptanceSample {
        issueDescription = issueDescription == null ? "" : issueDescription;
        reviewerUserId = reviewerUserId == null ? "" : reviewerUserId;
        if (itemId <= 0 || version < 0) throw new IllegalArgumentException("验收样本不合法");
        if (passed != null && (reviewerUserId.isBlank() || checkedAt == null)) {
            throw new IllegalArgumentException("样本检查人和时间不能为空");
        }
        if (Boolean.FALSE.equals(passed) && issueDescription.isBlank()) {
            throw new IllegalArgumentException("验收不通过必须填写问题说明");
        }
    }
}
