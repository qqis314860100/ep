package com.tianshu.assets.governance.acceptance.application;

@FunctionalInterface
public interface GovernanceJobDispatcher {

    void dispatch(long jobId);

    static GovernanceJobDispatcher noOp() {
        return ignored -> {};
    }
}
