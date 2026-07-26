package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.acceptance.application.GovernanceApplicationJobService;
import com.tianshu.assets.governance.acceptance.application.GovernanceJobDispatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringGovernanceJobDispatcher implements GovernanceJobDispatcher {

    private final TaskExecutor executor;
    private final GovernanceApplicationJobService applicationJobService;

    public SpringGovernanceJobDispatcher(
            @Qualifier("governanceJobExecutor") TaskExecutor executor,
            @Lazy GovernanceApplicationJobService applicationJobService) {
        this.executor = executor;
        this.applicationJobService = applicationJobService;
    }

    @Override
    public void dispatch(long jobId) {
        var task = (Runnable) () -> applicationJobService.run(jobId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }
}
