package com.tianshu.assets.dictionary.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 基于资产仓储的引用统计：按分类统计每个取值被多少份资产引用。
 * 统计口径为资产份数（同一资产内重复取值只计一次）；资产类型按枚举名与字典编码匹配。
 */
@Component
public class AssetBackedDictionaryUsageCounter implements DictionaryUsageCounter {

    private static final Set<String> COUNTED_CATEGORIES = Set.of(
            "SPECIALTY", "MODULE_TAG", "TAG", "PLATFORM_FAMILY", "PLATFORM_VARIANT",
            "PRODUCT_LINE", "BASE", "PRODUCTION_LINE", "PROCESS_SECTION", "ASSET_TYPE", "FILE_ROLE");

    private final AssetRepository assets;

    public AssetBackedDictionaryUsageCounter(AssetRepository assets) {
        this.assets = assets;
    }

    @Override
    public Map<String, Long> countsByCategory(String category) {
        if (category == null || !COUNTED_CATEGORIES.contains(category)) return Map.of();
        var counts = new HashMap<String, Long>();
        for (var asset : loadAssets()) {
            switch (category) {
                case "SPECIALTY" -> countDistinct(counts, asset.specialties());
                case "MODULE_TAG" -> countDistinct(counts, asset.moduleTags());
                case "TAG" -> countDistinct(counts, asset.tags());
                case "PLATFORM_FAMILY" -> countScopeValues(counts, asset, AssetScopeValue.PLATFORM_FAMILY);
                case "PLATFORM_VARIANT" -> countScopeValues(counts, asset, AssetScopeValue.PLATFORM_VARIANT);
                case "PRODUCT_LINE" -> countScopeValues(counts, asset, AssetScopeValue.PRODUCT_LINE);
                case "BASE" -> countScopeValues(counts, asset, AssetScopeValue.BASE);
                case "PRODUCTION_LINE" -> countScopeValues(counts, asset, AssetScopeValue.PRODUCTION_LINE);
                case "PROCESS_SECTION" -> countScopeValues(counts, asset, AssetScopeValue.PROCESS_SECTION);
                case "ASSET_TYPE" -> counts.merge(asset.assetType().name(), 1L, Long::sum);
                case "FILE_ROLE" -> countDistinct(counts, asset.files().stream()
                        .map(file -> file.role())
                        .filter(role -> role != null && !role.isBlank())
                        .toList());
                default -> { }
            }
        }
        return counts;
    }

    private void countDistinct(Map<String, Long> counts, List<String> values) {
        var seen = new HashSet<String>();
        for (var value : values) {
            if (value == null || value.isBlank()) continue;
            if (seen.add(value)) counts.merge(value, 1L, Long::sum);
        }
    }

    private void countScopeValues(Map<String, Long> counts, Asset asset, AssetScopeValue dimension) {
        var seen = new HashSet<String>();
        for (var scope : asset.scopes()) {
            var value = switch (dimension) {
                case PLATFORM_FAMILY -> scope.platformFamily();
                case PLATFORM_VARIANT -> scope.platformVariant();
                case PRODUCT_LINE -> scope.productLine();
                case BASE -> scope.base();
                case PRODUCTION_LINE -> scope.productionLine();
                case PROCESS_SECTION -> scope.processSection();
            };
            if (value == null || value.isBlank()) continue;
            if (seen.add(value)) counts.merge(value, 1L, Long::sum);
        }
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

    private enum AssetScopeValue { PLATFORM_FAMILY, PLATFORM_VARIANT, PRODUCT_LINE, BASE, PRODUCTION_LINE, PROCESS_SECTION }
}
