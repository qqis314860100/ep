package com.tianshu.assets.governance.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort.AssetResponsibility;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 资产责任人指派。为资产建立当前有效责任人，使新建/待整理资产可进入
 * 治理闭环的业务确认环节（确认人必须匹配当前有效责任人）。
 */
@Service
public class GovernanceResponsibilityService {

    private final AssetResponsibilityPort responsibilities;
    private final AssetRepository assets;
    private final GovernanceEmployeeDirectory employees;

    @Autowired
    public GovernanceResponsibilityService(
            AssetResponsibilityPort responsibilities,
            AssetRepository assets,
            GovernanceEmployeeDirectory employees) {
        this.responsibilities = responsibilities;
        this.assets = assets;
        this.employees = employees;
    }

    public AssetResponsibility assign(long assetId, String responsibleUserId, String responsibilityScope) {
        requireAsset(assetId);
        if (responsibleUserId == null || responsibleUserId.isBlank()) {
            throw new GovernanceValidationException("责任人不能为空");
        }
        var known = employees.findAllEmployees().stream()
                .map(com.tianshu.assets.governance.domain.GovernanceEmployee::id)
                .anyMatch(id -> id.equals(responsibleUserId));
        if (!known) {
            throw new GovernanceValidationException("责任人不在组织目录中");
        }
        if (responsibilityScope == null || responsibilityScope.isBlank()) {
            throw new GovernanceValidationException("责任范围不能为空");
        }
        return responsibilities.assign(assetId, responsibleUserId, responsibilityScope.trim());
    }

    public AssetResponsibility current(long assetId) {
        requireAsset(assetId);
        return responsibilities.currentResponsibility(assetId)
                .orElseThrow(() -> new GovernanceNotFoundException("资产责任关系不存在"));
    }

    private void requireAsset(long assetId) {
        if (assets.findById(assetId).isEmpty()) {
            throw new GovernanceNotFoundException("资产不存在");
        }
    }
}
