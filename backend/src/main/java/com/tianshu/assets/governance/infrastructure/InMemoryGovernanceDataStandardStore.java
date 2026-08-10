package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardRule;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardRuleType;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceDataStandardStore implements GovernanceDataStandardStore {

    private final Map<Long, GovernanceDataStandard> standards = new ConcurrentHashMap<>();
    private final Map<Long, GovernanceStandardImpactReview> impactReviews = new ConcurrentHashMap<>();
    private final AtomicLong nextStandardId = new AtomicLong(100);
    private final AtomicLong nextReviewId = new AtomicLong(1);

    public InMemoryGovernanceDataStandardStore() {
        var now = Instant.parse("2026-08-01T00:00:00Z");
        standards.put(1L, new GovernanceDataStandard(
                1, "FIELD-COMPLETENESS", 1, "数模资产完整性标准", GovernanceStandardStatus.ENABLED,
                List.of(AssetType.THREE_DIMENSIONAL_MODEL, AssetType.TWO_DIMENSIONAL_DRAWING,
                        AssetType.MIXED_ASSET),
                "emp-zhang", "张伟", now, "建立治理任务字段补充与质量验收基线", 6,
                List.of(
                        new GovernanceStandardRule("specialty", GovernanceStandardRuleType.REQUIRED,
                                "专业类别必须来自启用字典", true, "{\"dictionary\":\"SPECIALTY\"}"),
                        new GovernanceStandardRule("scope", GovernanceStandardRuleType.REQUIRED,
                                "适用范围必须匹配完整产品和生产范围", true, "{}"),
                        new GovernanceStandardRule("fileRole", GovernanceStandardRuleType.FILE_ROLE,
                                "每个资产包需要明确主文件和文件角色", true, "{}")),
                0, now, now));
    }

    @Override
    public List<GovernanceDataStandard> findAll() {
        return new ArrayList<>(standards.values());
    }

    @Override
    public Optional<GovernanceDataStandard> findById(long id) {
        return Optional.ofNullable(standards.get(id));
    }

    @Override
    public Optional<GovernanceDataStandard> findByCodeAndStandardVersion(String code, long standardVersion) {
        return standards.values().stream()
                .filter(item -> item.standardCode().equalsIgnoreCase(code)
                        && item.standardVersion() == standardVersion)
                .findFirst();
    }

    @Override
    public Optional<GovernanceDataStandard> findEnabledByCode(String code) {
        return standards.values().stream()
                .filter(item -> item.standardCode().equalsIgnoreCase(code)
                        && item.status() == GovernanceStandardStatus.ENABLED)
                .findFirst();
    }

    @Override
    public synchronized GovernanceDataStandard create(GovernanceDataStandard standard) {
        if (findByCodeAndStandardVersion(standard.standardCode(), standard.standardVersion()).isPresent()) {
            throw new GovernanceConflictException("同编码、同版本的数据标准已存在，不能覆盖");
        }
        var now = standard.createdAt() == null ? Instant.now() : standard.createdAt();
        var created = copy(standard, nextStandardId.getAndIncrement(), GovernanceStandardStatus.DRAFT,
                null, 0, 0, now, now);
        standards.put(created.id(), created);
        return created;
    }

    @Override
    public synchronized GovernanceDataStandard enable(
            long id, long expectedVersion, long affectedAssetCount, Instant effectiveAt) {
        var current = requireVersion(id, expectedVersion);
        if (current.status() != GovernanceStandardStatus.DRAFT) {
            throw new GovernanceConflictException("只有草稿数据标准可以启用");
        }
        standards.values().stream()
                .filter(item -> item.id() != id
                        && item.standardCode().equals(current.standardCode())
                        && item.status() == GovernanceStandardStatus.ENABLED)
                .toList()
                .forEach(item -> standards.put(item.id(), copy(
                        item, item.id(), GovernanceStandardStatus.DISABLED, item.effectiveAt(),
                        item.affectedAssetCount(), item.version() + 1, item.createdAt(), effectiveAt)));
        var enabled = copy(current, id, GovernanceStandardStatus.ENABLED, effectiveAt, affectedAssetCount,
                current.version() + 1, current.createdAt(), effectiveAt);
        standards.put(id, enabled);
        return enabled;
    }

    @Override
    public synchronized GovernanceDataStandard disable(long id, long expectedVersion, Instant updatedAt) {
        var current = requireVersion(id, expectedVersion);
        if (current.status() != GovernanceStandardStatus.ENABLED) {
            throw new GovernanceConflictException("只有已启用数据标准可以停用");
        }
        var disabled = copy(current, id, GovernanceStandardStatus.DISABLED, current.effectiveAt(),
                current.affectedAssetCount(), current.version() + 1, current.createdAt(), updatedAt);
        standards.put(id, disabled);
        return disabled;
    }

    @Override
    public GovernanceStandardImpactReview createImpactReview(
            long standardId, List<Long> assetIds, Instant createdAt) {
        var review = new GovernanceStandardImpactReview(
                nextReviewId.getAndIncrement(), standardId, assetIds.size(), assetIds,
                GovernanceStandardImpactReview.Status.OPEN, createdAt);
        impactReviews.put(review.id(), review);
        return review;
    }

    @Override
    public List<GovernanceStandardImpactReview> findImpactReviews(long standardId) {
        return impactReviews.values().stream()
                .filter(review -> review.standardId() == standardId)
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private GovernanceDataStandard requireVersion(long id, long expectedVersion) {
        var current = findById(id).orElseThrow(() -> new GovernanceConflictException("数据标准已不存在"));
        if (current.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("数据标准已被其他用户更新，请刷新后重试");
        }
        return current;
    }

    private GovernanceDataStandard copy(
            GovernanceDataStandard source, long id, GovernanceStandardStatus status, Instant effectiveAt,
            long affectedAssetCount, long version, Instant createdAt, Instant updatedAt) {
        return new GovernanceDataStandard(
                id, source.standardCode(), source.standardVersion(), source.name(), status,
                source.applicableAssetTypes(), source.ownerUserId(), source.ownerName(), effectiveAt,
                source.changeSummary(), affectedAssetCount, source.rules(), version, createdAt, updatedAt);
    }
}
