package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import java.util.List;

public class InMemoryGovernanceEmployeeDirectory implements GovernanceEmployeeDirectory {

    private final List<GovernanceEmployee> employees = List.of(
            new GovernanceEmployee("emp-chen", "陈工", "制造工程部", "OFFICE_DIRECTORY"),
            new GovernanceEmployee("emp-li", "李工", "标准化小组", "OFFICE_DIRECTORY"),
            new GovernanceEmployee("emp-wang", "王工", "资料管理组", "OFFICE_DIRECTORY"));

    @Override
    public List<GovernanceEmployee> findAllEmployees() {
        return employees;
    }
}
