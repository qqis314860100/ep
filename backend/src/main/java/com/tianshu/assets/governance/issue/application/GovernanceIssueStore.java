package com.tianshu.assets.governance.issue.application;

import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.util.List;

public interface GovernanceIssueStore {

    List<GovernanceIssue> find(GovernanceField field, GovernanceIssueStatus status, Long assetId);

    List<GovernanceIssue> findByIds(List<Long> issueIds);

    List<GovernanceIssue> insertAll(List<GovernanceIssue> issues);

    void claimOpen(List<GovernanceIssue> expectedIssues, long taskId);

    List<GovernanceIssue> findClaimedByTask(long taskId);

    default GovernanceIssue resolve(long issueId, long expectedVersion) {
        throw new UnsupportedOperationException("当前治理问题存储不支持解决问题");
    }
}
