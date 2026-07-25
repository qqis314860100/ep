package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceTaskApplicationService {

    private final GovernanceTaskStore store;
    private final GovernanceEmployeeDirectory employeeDirectory;

    @Autowired
    public GovernanceTaskApplicationService(
            GovernanceTaskStore store,
            GovernanceEmployeeDirectory employeeDirectory) {
        this.store = store;
        this.employeeDirectory = employeeDirectory;
    }

    public GovernanceTaskApplicationService(GovernanceTaskStore store) {
        this(store, store instanceof GovernanceEmployeeDirectory directory ? directory : List::of);
    }

    public List<GovernanceTask> list() {
        return store.findAll();
    }

    public GovernanceTask get(long taskId) {
        return store.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
    }

    public List<GovernancePlan> plans(long taskId) {
        get(taskId);
        return store.findPlans(taskId);
    }

    public List<GovernanceEmployee> employees() {
        return employeeDirectory.findAllEmployees();
    }

    public GovernanceTask requireClosedLoop(long taskId) {
        var task = get(taskId);
        if (task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        return task;
    }

    public GovernanceTask rejectTaskCreation() {
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }

    public GovernanceTask rejectTaskMutation(long taskId) {
        requireClosedLoop(taskId);
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }

    public GovernancePlan rejectPlanMutation(long taskId) {
        requireClosedLoop(taskId);
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }
}
