package com.tianshu.assets.governance.mapping.application;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.dictionary.application.DictionaryStore;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceNotFoundException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardStatus;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceMappingRuleService {
    private final GovernanceMappingRuleStore store;
    private final DictionaryStore dictionaryStore;
    private final GovernanceDataStandardStore standardStore;
    private final GovernanceRuleCatalog ruleCatalog;
    private final Clock clock;

    @Autowired
    public GovernanceMappingRuleService(
            GovernanceMappingRuleStore store,
            DictionaryStore dictionaryStore,
            GovernanceDataStandardStore standardStore,
            GovernanceRuleCatalog ruleCatalog) {
        this(store, dictionaryStore, standardStore, ruleCatalog, Clock.systemUTC());
    }

    GovernanceMappingRuleService(
            GovernanceMappingRuleStore store,
            DictionaryStore dictionaryStore,
            GovernanceDataStandardStore standardStore,
            GovernanceRuleCatalog ruleCatalog,
            Clock clock) {
        this.store = store;
        this.dictionaryStore = dictionaryStore;
        this.standardStore = standardStore;
        this.ruleCatalog = ruleCatalog;
        this.clock = clock;
    }

    public List<GovernanceMappingRule> list(GovernanceMappingStatus status, String sourceDimension, String query) {
        var normalizedDimension = sourceDimension == null ? "" : sourceDimension.trim();
        var normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return store.findAll().stream()
                .filter(rule -> status == null || rule.status() == status)
                .filter(rule -> normalizedDimension.isEmpty() || rule.sourceDimension().equals(normalizedDimension))
                .filter(rule -> normalizedQuery.isEmpty()
                        || rule.sourceValue().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || rule.targetName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || rule.targetCode().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(GovernanceMappingRule::sourceDimension)
                        .thenComparing(GovernanceMappingRule::sourceValue)
                        .thenComparing(Comparator.comparingLong(GovernanceMappingRule::ruleVersion).reversed()))
                .toList();
    }

    public GovernanceMappingRule get(long id) {
        return store.findById(id).orElseThrow(() -> new GovernanceNotFoundException("映射规则不存在"));
    }

    public GovernanceMappingRule create(CreateCommand command) {
        validate(command);
        var standard = standardStore.findById(command.standardId())
                .orElseThrow(() -> new GovernanceNotFoundException("数据标准不存在"));
        if (standard.status() == GovernanceStandardStatus.DISABLED) {
            throw new GovernanceConflictException("停用的数据标准不能新增映射规则");
        }
        var dictionary = dictionaryStore.findById(command.targetDictionaryItemId())
                .orElseThrow(() -> new GovernanceNotFoundException("目标字典项不存在"));
        if (!dictionary.category().equals(command.targetDictionaryCategory())) {
            throw new GovernanceValidationException("目标字典项分类与映射规则不一致");
        }
        if (dictionary.status() != DictionaryStatus.ENABLED) {
            throw new GovernanceValidationException("目标字典项必须处于启用状态");
        }
        validateScope(command.scope());
        var duplicate = store.findAll().stream().anyMatch(existing -> sameKey(existing, command, standard.standardCode(), standard.standardVersion(), 1));
        if (duplicate) throw new GovernanceConflictException("同一来源和适用范围的映射规则已存在，不能覆盖");
        var now = Instant.now(clock);
        return store.create(new GovernanceMappingRule(
                0, standard.id(), standard.standardCode(), standard.standardVersion(), 1,
                command.sourceDimension().trim(), command.sourceValue().trim(), dictionary.category(), dictionary.id(),
                dictionary.code(), dictionary.name(), normalizeScope(command.scope()), command.ambiguous(), null, null,
                null, null, 0, 0, command.affectedAssetCount(), GovernanceMappingStatus.PENDING_CONFIRMATION,
                0, now, now));
    }

    public GovernanceMappingRule createVersion(long sourceId, VersionCommand command) {
        var source = get(sourceId);
        var standard = standardStore.findById(source.standardId())
                .orElseThrow(() -> new GovernanceNotFoundException("数据标准不存在"));
        if (standard.status() == GovernanceStandardStatus.DISABLED) {
            throw new GovernanceConflictException("停用的数据标准不能新增映射规则");
        }
        validate(command);
        if (command.standardId() != source.standardId() || command.standardVersion() != source.standardVersion()) {
            throw new GovernanceValidationException("映射规则版本必须属于同一数据标准版本");
        }
        var dictionary = dictionaryStore.findById(command.targetDictionaryItemId())
                .orElseThrow(() -> new GovernanceNotFoundException("目标字典项不存在"));
        if (!dictionary.category().equals(command.targetDictionaryCategory()) || dictionary.status() != DictionaryStatus.ENABLED) {
            throw new GovernanceValidationException("目标字典项分类或状态不正确");
        }
        validateScope(command.scope());
        var nextVersion = store.findAll().stream().filter(item -> item.standardId() == source.standardId()
                && item.standardVersion() == source.standardVersion()
                && item.sourceDimension().equals(source.sourceDimension())
                && item.sourceValue().equals(source.sourceValue())
                && item.scope().equals(source.scope()))
                .mapToLong(GovernanceMappingRule::ruleVersion).max().orElse(source.ruleVersion()) + 1;
        var now = Instant.now(clock);
        return store.create(new GovernanceMappingRule(
                0, source.standardId(), source.standardCode(), source.standardVersion(), nextVersion,
                command.sourceDimension().trim(), command.sourceValue().trim(), dictionary.category(), dictionary.id(),
                dictionary.code(), dictionary.name(), normalizeScope(command.scope()), command.ambiguous(), null, null,
                null, null, 0, 0, command.affectedAssetCount(), GovernanceMappingStatus.PENDING_CONFIRMATION,
                0, now, now));
    }

    public GovernanceMappingRule confirm(long id, ConfirmCommand command) {
        var rule = get(id);
        if (rule.status() != GovernanceMappingStatus.PENDING_CONFIRMATION) {
            throw new GovernanceConflictException("只有待确认映射规则可以确认");
        }
        if (rule.ambiguous() && (command == null || command.comment() == null || command.comment().isBlank())) {
            throw new GovernanceValidationException("歧义映射必须填写业务确认意见");
        }
        if (command == null || command.userId() == null || command.userId().isBlank()) {
            throw new GovernanceValidationException("确认人不能为空");
        }
        return store.confirm(id, command.version(), command.userId().trim(), command.userName(), command.comment(), Instant.now(clock));
    }

    public GovernanceMappingRule disable(long id, long expectedVersion) {
        var rule = get(id);
        if (rule.status() != GovernanceMappingStatus.CONFIRMED) {
            throw new GovernanceConflictException("只有已确认映射规则可以停用");
        }
        return store.disable(id, expectedVersion, Instant.now(clock));
    }

    public record CreateCommand(long standardId, String sourceDimension, String sourceValue,
            String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope,
            boolean ambiguous, long affectedAssetCount) {}

    public record VersionCommand(long standardId, long standardVersion, String sourceDimension, String sourceValue,
            String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope,
            boolean ambiguous, long affectedAssetCount) {}

    public record ConfirmCommand(long version, String userId, String userName, String comment) {}

    private void validate(CreateCommand command) {
        if (command == null) throw new GovernanceValidationException("映射规则不能为空");
        validateCommon(command.sourceDimension(), command.sourceValue(), command.targetDictionaryCategory(), command.targetDictionaryItemId(), command.scope(), command.affectedAssetCount());
    }

    private void validate(VersionCommand command) {
        if (command == null) throw new GovernanceValidationException("映射规则不能为空");
        validateCommon(command.sourceDimension(), command.sourceValue(), command.targetDictionaryCategory(), command.targetDictionaryItemId(), command.scope(), command.affectedAssetCount());
    }

    private void validateCommon(String dimension, String value, String category, long itemId, AssetScope scope, long affected) {
        if (dimension == null || dimension.isBlank()) throw new GovernanceValidationException("来源维度不能为空");
        if (value == null || value.isBlank()) throw new GovernanceValidationException("来源值不能为空");
        if (category == null || category.isBlank() || itemId <= 0) throw new GovernanceValidationException("目标字典项不能为空");
        if (affected < 0) throw new GovernanceValidationException("潜在影响数量不能为负数");
        validateScope(scope);
    }

    private void validateScope(AssetScope scope) {
        if (scope == null || scope.platform().isBlank() || scope.productLine().isBlank() || scope.base().isBlank()
                || scope.productionLine().isBlank() || scope.processSection().isBlank()
                || scope.platformFamily().isBlank() || scope.platformVariant().isBlank()) {
            throw new GovernanceValidationException("适用范围必须填写完整产品和生产条件");
        }
        var validScopes = ruleCatalog.validScopes();
        if (!validScopes.isEmpty() && validScopes.stream().noneMatch(scope::equals)) {
            throw new GovernanceValidationException("产品与生产条件必须来自同一个适用范围");
        }
    }

    private boolean sameKey(GovernanceMappingRule existing, CreateCommand command, String code, long version, long ruleVersion) {
        return existing.standardCode().equals(code) && existing.standardVersion() == version
                && existing.ruleVersion() == ruleVersion && existing.sourceDimension().equals(command.sourceDimension().trim())
                && existing.sourceValue().equals(command.sourceValue().trim()) && existing.scope().equals(command.scope());
    }

    private AssetScope normalizeScope(AssetScope scope) {
        return new AssetScope(scope.platform(), scope.productLine(), scope.base(), scope.productionLine(), scope.processSection(), scope.platformFamily(), scope.platformVariant());
    }
}
