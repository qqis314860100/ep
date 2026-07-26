package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import java.util.List;
import java.util.Optional;

public interface GovernanceTaskStore {

    List<GovernanceTask> findAll();

    Optional<GovernanceTask> findById(long taskId);

    GovernanceTask insert(GovernanceTask task);

    GovernanceTask update(GovernanceTask task, long expectedVersion);

    List<GovernancePlan> findPlans(long taskId);

    GovernancePlan insertPlan(GovernancePlan plan);
}
