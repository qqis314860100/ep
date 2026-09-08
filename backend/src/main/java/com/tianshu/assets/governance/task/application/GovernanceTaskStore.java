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

    /** 任务移交专用更新：仅改派负责人（owner/assignee 同步换人），不触碰其它字段，乐观锁推进版本。 */
    GovernanceTask reassign(long taskId, String ownerUserId, String ownerName, long expectedVersion);

    List<GovernancePlan> findPlans(long taskId);

    GovernancePlan insertPlan(GovernancePlan plan);
}
