package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.domain.GovernanceEmployee;
import java.util.List;

public interface GovernanceEmployeeDirectory {

    List<GovernanceEmployee> findAllEmployees();
}
