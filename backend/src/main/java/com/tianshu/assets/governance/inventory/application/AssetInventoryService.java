package com.tianshu.assets.governance.inventory.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 资产盘点（GOVERN-01）：总量/状态/疑似重复/异常文件统计、四项治理率、
 * 旧维度与缺字段筛选。口径以资产份数为单位。
 */
@Service
public class AssetInventoryService {

    private final AssetRepository assets;
    private final GovernanceIssueStore issues;

    public AssetInventoryService(AssetRepository assets, GovernanceIssueStore issues) {
        this.assets = assets;
        this.issues = issues;
    }

    public InventoryView inventory(String legacyPlatform, String legacyLine, String legacyCategory,
            String owner, String format, boolean missingBase, boolean missingLine,
            boolean missingDescription, boolean missingOwner, boolean missingFile,
            int page, int perPage) {
        var all = loadAssets();
        var criteria = new InventoryCriteria(legacyPlatform, legacyLine, legacyCategory, owner, format,
                missingBase, missingLine, missingDescription, missingOwner, missingFile);
        var filtered = all.stream().filter(asset -> matches(asset, criteria)).toList();
        var claimedAssetIds = claimedAssetIds();
        return buildView(filtered, claimedAssetIds, page, perPage);
    }

    private InventoryView buildView(List<Asset> filtered, Set<Long> claimedAssetIds, int page, int perPage) {
        var duplicateNumbers = filtered.stream()
                .filter(asset -> !asset.assetNumber().isBlank())
                .collect(Collectors.groupingBy(Asset::assetNumber));
        long duplicates = duplicateNumbers.values().stream().filter(group -> group.size() > 1).count();
        long pending = filtered.stream().filter(asset -> asset.status() == AssetStatus.PENDING_CURATION).count();
        long standardized = filtered.stream().filter(asset -> asset.status() == AssetStatus.STANDARDIZED).count();
        long claimed = filtered.stream().filter(asset -> claimedAssetIds.contains(asset.id())).count();
        long anomalousFiles = filtered.stream().filter(asset -> asset.files().isEmpty()
                || asset.files().stream().noneMatch(file -> file.primary())).count();
        long total = filtered.size();
        long complete = filtered.stream().filter(this::isComplete).count();
        long withScope = filtered.stream().filter(asset -> asset.scopes().stream().anyMatch(AssetScope::complete)).count();
        long withOwner = filtered.stream().filter(asset -> !asset.ownerName().isBlank()).count();
        long withFile = filtered.stream().filter(asset -> !asset.files().isEmpty()).count();

        var offset = (long) (page - 1) * perPage;
        var items = filtered.stream().skip(offset).limit(perPage)
                .map(asset -> new InventoryItem(asset.id(), asset.assetNumber(), asset.name(),
                        asset.assetType().name(), asset.status().name(), asset.ownerName(),
                        legacyPlatformOf(asset), legacyLineOf(asset),
                        lacksScope(asset), lacksLine(asset),
                        asset.description().isBlank(), asset.ownerName().isBlank(),
                        asset.files().isEmpty() || asset.files().stream().noneMatch(file -> file.primary()),
                        claimedAssetIds.contains(asset.id())))
                .toList();
        var rates = new InventoryRates(rate(complete, total), rate(withScope, total),
                rate(withOwner, total), rate(withFile, total));
        return new InventoryView(new InventoryTotals(total, pending, claimed, standardized, duplicates, anomalousFiles),
                rates, items, new InventoryMeta(total, page, perPage));
    }

    private boolean matches(Asset asset, InventoryCriteria criteria) {
        if (criteria.missingBase() && !lacksScope(asset)) return false;
        if (criteria.missingLine() && !lacksLine(asset)) return false;
        if (criteria.missingDescription() && !asset.description().isBlank()) return false;
        if (criteria.missingOwner() && !asset.ownerName().isBlank()) return false;
        if (criteria.missingFile() && !(asset.files().isEmpty()
                || asset.files().stream().noneMatch(file -> file.primary()))) return false;
        if (!criteria.legacyPlatform().isBlank()
                && !legacyPlatformOf(asset).toLowerCase(Locale.ROOT).contains(criteria.legacyPlatform().toLowerCase(Locale.ROOT))) return false;
        if (!criteria.legacyLine().isBlank()
                && !legacyLineOf(asset).toLowerCase(Locale.ROOT).contains(criteria.legacyLine().toLowerCase(Locale.ROOT))) return false;
        if (!criteria.legacyCategory().isBlank()
                && !asset.assetType().name().equalsIgnoreCase(criteria.legacyCategory())) return false;
        if (!criteria.owner().isBlank() && !criteria.owner().equals(asset.ownerName())) return false;
        if (!criteria.format().isBlank()
                && asset.files().stream().noneMatch(file -> file.format().equalsIgnoreCase(criteria.format()))) return false;
        return true;
    }

    private boolean lacksScope(Asset asset) {
        return asset.scopes().stream().noneMatch(AssetScope::complete);
    }

    private boolean lacksLine(Asset asset) {
        return asset.scopes().stream().noneMatch(scope -> !scope.productionLine().isBlank());
    }

    private boolean isComplete(Asset asset) {
        return !asset.assetNumber().isBlank() && !asset.name().isBlank() && !asset.description().isBlank()
                && !asset.specialties().isEmpty() && !asset.files().isEmpty();
    }

    private String legacyPlatformOf(Asset asset) {
        return asset.scopes().stream().findFirst()
                .map(scope -> scope.platformFamily().isBlank() ? scope.platform() : scope.platformFamily())
                .orElse("");
    }

    private String legacyLineOf(Asset asset) {
        return asset.scopes().stream().findFirst().map(AssetScope::productionLine).orElse("");
    }

    private Set<Long> claimedAssetIds() {
        return issues.find(null, GovernanceIssueStatus.CLAIMED, null).stream()
                .map(issue -> issue.assetId())
                .collect(Collectors.toSet());
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private List<Asset> loadAssets() {
        var all = new ArrayList<Asset>();
        var page = 1;
        while (true) {
            var result = assets.search(new AssetSearchCriteria("", null, null, "", "", "", "", "", null, page, 1000));
            all.addAll(result.items());
            if (all.size() >= result.total() || result.items().isEmpty()) return all;
            page++;
        }
    }

    private record InventoryCriteria(String legacyPlatform, String legacyLine, String legacyCategory,
            String owner, String format, boolean missingBase, boolean missingLine,
            boolean missingDescription, boolean missingOwner, boolean missingFile) {
        private InventoryCriteria {
            legacyPlatform = legacyPlatform == null ? "" : legacyPlatform.trim();
            legacyLine = legacyLine == null ? "" : legacyLine.trim();
            legacyCategory = legacyCategory == null ? "" : legacyCategory.trim();
            owner = owner == null ? "" : owner.trim();
            format = format == null ? "" : format.trim();
        }
    }

    public record InventoryView(InventoryTotals totals, InventoryRates rates,
            List<InventoryItem> items, InventoryMeta meta) {}

    public record InventoryTotals(long total, long pendingCuration, long claimed, long standardized,
            long duplicateSuspects, long anomalousFiles) {}

    public record InventoryRates(double completeness, double scopeCoverage,
            double ownerCoverage, double fileAvailability) {}

    public record InventoryItem(long assetId, String assetNumber, String assetName,
            String assetType, String status, String ownerName,
            String legacyPlatform, String legacyLine,
            boolean missingBase, boolean missingLine, boolean missingDescription,
            boolean missingOwner, boolean missingFile, boolean claimed) {}

    public record InventoryMeta(long total, int page, int perPage) {}
}
