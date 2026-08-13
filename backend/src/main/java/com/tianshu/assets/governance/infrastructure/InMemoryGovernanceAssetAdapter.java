package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.acceptance.application.GovernanceAssetPort;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import java.util.LinkedHashMap;
import java.util.Map;

public class InMemoryGovernanceAssetAdapter implements GovernanceAssetPort {

    private final Map<Long, AssetState> assets = new LinkedHashMap<>();
    private final Map<Long, String> appliedItems = new LinkedHashMap<>();
    private final Map<Long, Integer> applyCounts = new LinkedHashMap<>();
    private final Map<Long, String> nextFailures = new LinkedHashMap<>();

    public synchronized void seed(long assetId, long version) {
        assets.putIfAbsent(assetId, new AssetState(AssetStatus.PENDING_CURATION, version));
    }

    @Override
    public synchronized GovernanceAssetSnapshot snapshot(long assetId) {
        var state = assets.computeIfAbsent(
                assetId, ignored -> new AssetState(AssetStatus.PENDING_CURATION, 0));
        return new GovernanceAssetSnapshot(assetId, state.status(), state.version());
    }

    @Override
    public synchronized ApplyOutcome applyFieldResult(
            long itemId,
            long assetId,
            GovernanceField field,
            String proposedValueJson,
            long expectedAssetVersion,
            String actorUserId) {
        var existing = appliedItems.get(itemId);
        if (existing != null) return new ApplyOutcome(snapshot(assetId).version(), existing);
        var failure = nextFailures.remove(itemId);
        if (failure != null) throw new IllegalStateException(failure);
        var wasAbsent = !assets.containsKey(assetId);
        var current = snapshot(assetId);
        if (current.version() != expectedAssetVersion) {
            // 惰性初始化的治理状态尚未与扫描盖章的资产版本对齐
            // （扫描使用 updatedAt 毫秒时间戳，而状态首次触达默认版本为 0），
            // 首次正式应用时以该版本为基线，使扫描产生的问题可完成闭环。
            if (wasAbsent) {
                assets.put(assetId, new AssetState(current.status(), expectedAssetVersion));
                current = snapshot(assetId);
            }
            if (current.version() != expectedAssetVersion) {
                throw new GovernanceVersionConflictException("资产版本已变化，无法正式应用");
            }
        }
        var summary = "字段 " + field.name() + " 已应用";
        appliedItems.put(itemId, summary);
        applyCounts.merge(itemId, 1, Integer::sum);
        assets.put(assetId, new AssetState(current.status(), current.version() + 1));
        return new ApplyOutcome(current.version() + 1, summary);
    }

    @Override
    public synchronized boolean meetsAllActiveStandards(long assetId) {
        snapshot(assetId);
        return true;
    }

    @Override
    public synchronized void markStandardized(long assetId, long expectedAssetVersion, String actorUserId) {
        var current = snapshot(assetId);
        if (current.status() == AssetStatus.STANDARDIZED) return;
        if (current.version() != expectedAssetVersion) {
            throw new GovernanceVersionConflictException("资产版本已变化，无法标记为已标准化");
        }
        assets.put(assetId, new AssetState(AssetStatus.STANDARDIZED, current.version() + 1));
    }

    public synchronized void failNextApplyFor(long itemId, String reason) {
        nextFailures.put(itemId, reason);
    }

    public synchronized int applyCount(long itemId) {
        return applyCounts.getOrDefault(itemId, 0);
    }

    public synchronized AssetStatus status(long assetId) {
        return snapshot(assetId).status();
    }

    private record AssetState(AssetStatus status, long version) {}
}
