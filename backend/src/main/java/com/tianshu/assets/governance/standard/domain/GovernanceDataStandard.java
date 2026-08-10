package com.tianshu.assets.governance.standard.domain;

import com.tianshu.assets.asset.domain.AssetType;
import java.time.Instant;
import java.util.List;

public record GovernanceDataStandard(
        long id,
        String standardCode,
        long standardVersion,
        String name,
        GovernanceStandardStatus status,
        List<AssetType> applicableAssetTypes,
        String ownerUserId,
        String ownerName,
        Instant effectiveAt,
        String changeSummary,
        long affectedAssetCount,
        List<GovernanceStandardRule> rules,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public GovernanceDataStandard {
        if (standardCode == null || standardCode.isBlank()) {
            throw new IllegalArgumentException("数据标准编码不能为空");
        }
        if (standardVersion <= 0) {
            throw new IllegalArgumentException("数据标准版本不合法");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("数据标准名称不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("数据标准状态不能为空");
        }
        if (ownerUserId == null || ownerUserId.isBlank() || ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("数据标准负责人不能为空");
        }
        standardCode = standardCode.trim().toUpperCase();
        name = name.trim();
        ownerUserId = ownerUserId.trim();
        ownerName = ownerName.trim();
        changeSummary = changeSummary == null ? "" : changeSummary.trim();
        applicableAssetTypes = applicableAssetTypes == null ? List.of() : List.copyOf(applicableAssetTypes);
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (affectedAssetCount < 0 || version < 0) {
            throw new IllegalArgumentException("数据标准计数或并发版本不合法");
        }
    }

    public GovernanceDataStandard asNewVersion(
            long nextStandardVersion,
            String nextName,
            List<AssetType> nextAssetTypes,
            String nextOwnerUserId,
            String nextOwnerName,
            String nextChangeSummary,
            List<GovernanceStandardRule> nextRules,
            Instant now) {
        return new GovernanceDataStandard(
                0, standardCode, nextStandardVersion, nextName, GovernanceStandardStatus.DRAFT,
                nextAssetTypes, nextOwnerUserId, nextOwnerName, null, nextChangeSummary, 0,
                nextRules, 0, now, now);
    }
}
