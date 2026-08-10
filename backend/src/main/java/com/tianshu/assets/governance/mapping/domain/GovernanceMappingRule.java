package com.tianshu.assets.governance.mapping.domain;

import com.tianshu.assets.asset.domain.AssetScope;
import java.time.Instant;

public record GovernanceMappingRule(
        long id,
        long standardId,
        String standardCode,
        long standardVersion,
        long ruleVersion,
        String sourceDimension,
        String sourceValue,
        String targetDictionaryCategory,
        long targetDictionaryItemId,
        String targetCode,
        String targetName,
        AssetScope scope,
        boolean ambiguous,
        String confirmationComment,
        String confirmedByUserId,
        String confirmedByName,
        Instant confirmedAt,
        long usageCount,
        long matchedAssetCount,
        long affectedAssetCount,
        GovernanceMappingStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
