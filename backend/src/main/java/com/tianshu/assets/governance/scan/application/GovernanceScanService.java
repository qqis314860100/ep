package com.tianshu.assets.governance.scan.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.dictionary.application.DictionaryStore;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import com.tianshu.assets.governance.application.GovernanceNotFoundException;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleStore;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceScanService {
    private final AssetRepository assetRepository;
    private final GovernanceIssueStore issueStore;
    private final GovernanceScanRunStore runStore;
    private final GovernanceDataStandardStore standardStore;
    private final GovernanceMappingRuleStore mappingStore;
    private final DictionaryStore dictionaryStore;
    private final GovernanceEmployeeDirectory employeeDirectory;
    private final GovernanceRuleCatalog ruleCatalog;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public GovernanceScanService(AssetRepository assetRepository, GovernanceIssueStore issueStore, GovernanceScanRunStore runStore,
            GovernanceDataStandardStore standardStore, GovernanceMappingRuleStore mappingStore, DictionaryStore dictionaryStore,
            GovernanceEmployeeDirectory employeeDirectory, GovernanceRuleCatalog ruleCatalog, ObjectMapper objectMapper) {
        this(assetRepository, issueStore, runStore, standardStore, mappingStore, dictionaryStore, employeeDirectory, ruleCatalog, objectMapper, Clock.systemUTC());
    }

    GovernanceScanService(AssetRepository assetRepository, GovernanceIssueStore issueStore, GovernanceScanRunStore runStore,
            GovernanceDataStandardStore standardStore, GovernanceMappingRuleStore mappingStore, DictionaryStore dictionaryStore,
            GovernanceEmployeeDirectory employeeDirectory, GovernanceRuleCatalog ruleCatalog, ObjectMapper objectMapper, Clock clock) {
        this.assetRepository = assetRepository; this.issueStore = issueStore; this.runStore = runStore; this.standardStore = standardStore;
        this.mappingStore = mappingStore; this.dictionaryStore = dictionaryStore; this.employeeDirectory = employeeDirectory;
        this.ruleCatalog = ruleCatalog; this.objectMapper = objectMapper; this.clock = clock;
    }

    public List<GovernanceScanRun> listRuns() { return runStore.findAll(); }
    public GovernanceScanRun getRun(long id) { return runStore.findById(id).orElseThrow(() -> new GovernanceNotFoundException("扫描运行不存在")); }

    @Transactional
    public GovernanceScanRun scan(GovernanceScanTriggerType triggerType, Long retryOfRunId) {
        var now = Instant.now(clock);
        var running = runStore.start(new GovernanceScanRun(0, triggerType, GovernanceScanRunStatus.RUNNING, now, null, 0, 0, 0, 0, "", retryOfRunId, 0));
        var counts = new MutableCounts();
        try {
            var standard = standardStore.findAll().stream().filter(item -> item.status().name().equals("ENABLED")).max(java.util.Comparator.comparingLong(GovernanceDataStandard::standardVersion)).orElseThrow(() -> new IllegalStateException("当前没有启用的数据标准"));
            var assets = loadAssets(); counts.scanned = assets.size();
            var duplicateNumbers = assets.stream().filter(asset -> !asset.assetNumber().isBlank()).collect(Collectors.groupingBy(Asset::assetNumber));
            for (var asset : assets) {
                for (var issue : detect(asset, standard, duplicateNumbers)) {
                    var existing = issueStore.findByFingerprint(issue.fingerprint());
                    var saved = issueStore.upsertScanned(issue);
                    if (existing.isEmpty()) counts.created++; else if (existing.get().status() == GovernanceIssueStatus.RESOLVED && saved.status() == GovernanceIssueStatus.OPEN) counts.reopened++; else counts.unchanged++;
                }
            }
            return runStore.succeed(running.id(), running.version(), counts.toCounts(), Instant.now(clock));
        } catch (RuntimeException exception) {
            return runStore.fail(running.id(), running.version(), counts.toCounts(), exception.getMessage(), Instant.now(clock));
        }
    }

    public GovernanceScanRun retry(long runId) {
        var source = getRun(runId);
        if (source.status() != GovernanceScanRunStatus.FAILED) throw new IllegalStateException("只有失败的扫描运行可以重试");
        return scan(GovernanceScanTriggerType.RETRY, source.id());
    }

    private List<Asset> loadAssets() {
        var all = new ArrayList<Asset>();
        var page = 1;
        while (true) {
            var result = assetRepository.search(new AssetSearchCriteria("", null, null, "", "", "", "", "", null, page, 1000));
            all.addAll(result.items());
            if (all.size() >= result.total() || result.items().isEmpty()) return all;
            page++;
        }
    }

    private List<GovernanceIssue> detect(Asset asset, GovernanceDataStandard standard, Map<String, List<Asset>> duplicateNumbers) {
        if (!standard.applicableAssetTypes().isEmpty() && !standard.applicableAssetTypes().contains(asset.assetType())) return List.of();
        var issues = new ArrayList<GovernanceIssue>();
        var ruleVersion = standard.standardVersion();
        var ruleCodes = standard.rules().stream().map(rule -> rule.targetField().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (ruleCodes.contains("description") && asset.description().isBlank()) issues.add(issue(asset, GovernanceField.DESCRIPTION, "MISSING_REQUIRED_FIELD", "/description", standard.standardCode(), ruleVersion, asset.description(), "HIGH", true));
        if (ruleCodes.contains("specialty") || ruleCodes.contains("specialties")) {
            var enabledSpecialties = dictionaryStore.findAll().stream().filter(item -> item.category().equals("SPECIALTY") && item.status() == DictionaryStatus.ENABLED).map(item -> item.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (asset.specialties().isEmpty()) issues.add(issue(asset, GovernanceField.SPECIALTIES, "MISSING_REQUIRED_FIELD", "/specialties", standard.standardCode(), ruleVersion, "[]", "HIGH", true));
            asset.specialties().stream().filter(value -> !enabledSpecialties.contains(value.toLowerCase(Locale.ROOT))).forEach(value -> issues.add(issue(asset, GovernanceField.SPECIALTIES, "INVALID_DICTIONARY_VALUE", "/specialties/" + value, standard.standardCode(), ruleVersion, json(value), "HIGH", true)));
        }
        if (ruleCodes.contains("scope")) {
            for (var index = 0; index < asset.scopes().size(); index++) {
                var scope = asset.scopes().get(index);
                if (!completeScope(scope) || (!ruleCatalog.validScopes().isEmpty() && ruleCatalog.validScopes().stream().noneMatch(scope::equals))) {
                    issues.add(issue(asset, GovernanceField.SCOPE, "INVALID_SCOPE", "/scopes/" + index, standard.standardCode(), ruleVersion, json(scope), "HIGH", true));
                }
            }
            if (asset.scopes().isEmpty()) issues.add(issue(asset, GovernanceField.SCOPE, "MISSING_REQUIRED_FIELD", "/scopes", standard.standardCode(), ruleVersion, "[]", "HIGH", true));
        }
        var validOwners = employeeDirectory.findAllEmployees().stream().map(employee -> employee.name()).collect(Collectors.toSet());
        if (asset.ownerName().isBlank() || !validOwners.contains(asset.ownerName())) issues.add(issue(asset, GovernanceField.OWNER, "INVALID_RESPONSIBILITY", "/ownerName", standard.standardCode(), ruleVersion, json(asset.ownerName()), "MEDIUM", false));
        if (asset.files().isEmpty() || asset.files().stream().noneMatch(file -> file.primary())) issues.add(issue(asset, GovernanceField.DESCRIPTION, "ANOMALOUS_FILE", "/files", standard.standardCode(), ruleVersion, json(asset.files()), "MEDIUM", false));
        if (!asset.assetNumber().isBlank() && duplicateNumbers.getOrDefault(asset.assetNumber(), List.of()).size() > 1 && duplicateNumbers.get(asset.assetNumber()).getFirst().id() != asset.id()) issues.add(issue(asset, GovernanceField.DESCRIPTION, "DUPLICATE_ASSET_NUMBER", "/assetNumber", "ASSET-UNIQUENESS", 1, json(asset.assetNumber()), "HIGH", true));
        for (var mapping : mappingStore.findAll()) if (mapping.status() == GovernanceMappingStatus.CONFIRMED && !mapping.ambiguous() && mapping.standardId() == standard.id()) addMappingSuggestions(asset, mapping, issues);
        return issues;
    }

    private void addMappingSuggestions(Asset asset, GovernanceMappingRule mapping, List<GovernanceIssue> issues) {
        for (var index = 0; index < asset.scopes().size(); index++) {
            var scope = asset.scopes().get(index);
            var source = mappingSource(mapping.sourceDimension(), scope);
            if (mapping.sourceValue().equals(source) && mapping.scope().equals(scope)) issues.add(issue(asset, GovernanceField.SCOPE, "MAPPING_SUGGESTION", "/scopes/" + index + "/" + mapping.sourceDimension(), mapping.standardCode(), mapping.ruleVersion(), json(source), "LOW", false));
        }
    }

    private String mappingSource(String dimension, AssetScope scope) {
        var value = dimension.toLowerCase(Locale.ROOT);
        if (value.contains("平台")) return scope.platform();
        if (value.contains("子类") || value.contains("车型")) return scope.platformVariant();
        if (value.contains("蓝本") || value.contains("产品")) return scope.productLine();
        if (value.contains("基地")) return scope.base();
        if (value.contains("拉线")) return scope.productionLine();
        if (value.contains("工序")) return scope.processSection();
        return "";
    }

    private boolean completeScope(AssetScope scope) { return scope != null && !scope.platformFamily().isBlank() && !scope.platformVariant().isBlank() && !scope.productLine().isBlank() && !scope.base().isBlank() && !scope.productionLine().isBlank() && !scope.processSection().isBlank(); }
    private GovernanceIssue issue(Asset asset, GovernanceField field, String type, String path, String ruleCode, long ruleVersion, Object original, String severity, boolean blocking) { return new GovernanceIssue(0, asset.id(), field, type, path, ruleCode, ruleVersion, json(original), assetVersion(asset), scopeFingerprint(asset), severity, blocking, GovernanceIssueStatus.OPEN, null, 0); }
    private long assetVersion(Asset asset) { return asset.updatedAt() == null ? 0 : asset.updatedAt().toEpochMilli(); }
    private String scopeFingerprint(Asset asset) { return digest(asset.scopes().stream().map(scope -> scope.platformFamily()+"|"+scope.platformVariant()+"|"+scope.productLine()+"|"+scope.base()+"|"+scope.productionLine()+"|"+scope.processSection()).collect(Collectors.joining(";"))); }
    private String digest(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("扫描指纹生成失败", exception); } }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { return String.valueOf(value); } }

    private static final class MutableCounts { long scanned; long created; long reopened; long unchanged; GovernanceScanRunStore.Counts toCounts() { return new GovernanceScanRunStore.Counts(scanned, created, reopened, unchanged); } }
}
