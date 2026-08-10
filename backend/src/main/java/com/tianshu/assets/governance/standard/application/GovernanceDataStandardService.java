package com.tianshu.assets.governance.standard.application;

import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceNotFoundException;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardRule;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceDataStandardService {

    private final GovernanceDataStandardStore store;
    private final GovernanceStandardImpactPort impactPort;
    private final Clock clock;

    @Autowired
    public GovernanceDataStandardService(
            GovernanceDataStandardStore store, GovernanceStandardImpactPort impactPort) {
        this(store, impactPort, Clock.systemUTC());
    }

    GovernanceDataStandardService(
            GovernanceDataStandardStore store, GovernanceStandardImpactPort impactPort, Clock clock) {
        this.store = store;
        this.impactPort = impactPort;
        this.clock = clock;
    }

    public List<GovernanceDataStandard> list() {
        return store.findAll().stream()
                .sorted(Comparator.comparing(GovernanceDataStandard::standardCode)
                        .thenComparing(Comparator.comparingLong(
                                GovernanceDataStandard::standardVersion).reversed()))
                .toList();
    }

    public GovernanceDataStandard get(long id) {
        return store.findById(id)
                .orElseThrow(() -> new GovernanceNotFoundException("数据标准不存在"));
    }

    public GovernanceDataStandard create(CreateCommand command) {
        validateCreate(command);
        var code = command.standardCode().trim().toUpperCase();
        if (store.findByCodeAndStandardVersion(code, command.standardVersion()).isPresent()) {
            throw new GovernanceConflictException("同编码、同版本的数据标准已存在，不能覆盖");
        }
        var now = Instant.now(clock);
        return store.create(new GovernanceDataStandard(
                0, code, command.standardVersion(), command.name(), GovernanceStandardStatus.DRAFT,
                command.applicableAssetTypes(), command.ownerUserId(), command.ownerName(), null,
                command.changeSummary(), 0, command.rules(), 0, now, now));
    }

    public GovernanceDataStandard createVersion(long sourceId, CreateVersionCommand command) {
        var source = get(sourceId);
        if (command.standardVersion() <= source.standardVersion()) {
            throw new IllegalArgumentException("新标准版本必须高于来源版本");
        }
        return create(new CreateCommand(
                source.standardCode(), command.standardVersion(), command.name(),
                command.applicableAssetTypes(), command.ownerUserId(), command.ownerName(),
                command.changeSummary(), command.rules()));
    }

    @Transactional
    public ActivationResult enable(long id, long expectedVersion) {
        var standard = get(id);
        if (standard.status() != GovernanceStandardStatus.DRAFT) {
            throw new GovernanceConflictException("只有草稿数据标准可以启用");
        }
        if (standard.rules().isEmpty()) {
            throw new GovernanceConflictException("数据标准至少需要一条规则才能启用");
        }
        var affectedAssetIds = impactPort.findPotentiallyAffectedAssetIds(standard.applicableAssetTypes());
        var affectedCount = affectedAssetIds.size();
        var now = Instant.now(clock);
        var enabled = store.enable(id, expectedVersion, affectedCount, now);
        var review = store.createImpactReview(enabled.id(), affectedAssetIds, now);
        return new ActivationResult(enabled, review);
    }

    public GovernanceDataStandard disable(long id, long expectedVersion) {
        var standard = get(id);
        if (standard.status() != GovernanceStandardStatus.ENABLED) {
            throw new GovernanceConflictException("只有已启用数据标准可以停用");
        }
        return store.disable(id, expectedVersion, Instant.now(clock));
    }

    public List<GovernanceStandardImpactReview> impactReviews(long id) {
        get(id);
        return store.findImpactReviews(id);
    }

    private void validateCreate(CreateCommand command) {
        if (command == null) throw new IllegalArgumentException("数据标准不能为空");
        if (command.standardCode() == null || command.standardCode().isBlank()) {
            throw new IllegalArgumentException("数据标准编码不能为空");
        }
        if (command.standardVersion() <= 0) {
            throw new IllegalArgumentException("数据标准版本不合法");
        }
    }

    public record CreateCommand(
            String standardCode,
            long standardVersion,
            String name,
            List<AssetType> applicableAssetTypes,
            String ownerUserId,
            String ownerName,
            String changeSummary,
            List<GovernanceStandardRule> rules) {}

    public record CreateVersionCommand(
            long standardVersion,
            String name,
            List<AssetType> applicableAssetTypes,
            String ownerUserId,
            String ownerName,
            String changeSummary,
            List<GovernanceStandardRule> rules) {}

    public record ActivationResult(
            GovernanceDataStandard standard, GovernanceStandardImpactReview impactReview) {}
}
