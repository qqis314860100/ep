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
        dispatchTask(task);
    }

    /** 通用任务投递（复用治理调度的事务提交后置与执行线程），供 AI 编目等异步任务使用，不另建队列。 */
    public void dispatchTask(Runnable task) {
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
