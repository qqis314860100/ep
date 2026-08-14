package com.tianshu.assets.dictionary.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import org.junit.jupiter.api.Test;

class AssetBackedDictionaryUsageCounterTest {

    private final AssetBackedDictionaryUsageCounter counter =
            new AssetBackedDictionaryUsageCounter(new InMemoryAssetRepository());

    @Test
    void countsSpecialtyTagsAndScopesFromAssets() {
        assertThat(counter.countsByCategory("SPECIALTY"))
                .containsEntry("机械", 5L)
                .containsEntry("电气", 1L)
                .containsEntry("工装", 2L);
        assertThat(counter.countsByCategory("BASE"))
                .containsEntry("宁德基地", 3L)
                .containsEntry("溧阳基地", 2L);
        assertThat(counter.countsByCategory("PRODUCT_LINE")).containsEntry("H03", 3L);
        assertThat(counter.countsByCategory("MODULE_TAG")).containsEntry("标准设备模块", 1L);
    }

    @Test
    void countsAssetTypeByCode() {
        assertThat(counter.countsByCategory("ASSET_TYPE"))
                .containsEntry("MIXED_ASSET", 2L)
                .containsEntry("TWO_DIMENSIONAL_DRAWING", 2L)
                .containsEntry("THREE_DIMENSIONAL_MODEL", 1L);
    }

    @Test
    void leavesUncountedCategoriesEmpty() {
        assertThat(counter.countsByCategory("DOCUMENT_CATEGORY")).isEmpty();
        assertThat(counter.countsByCategory(null)).isEmpty();
    }

    @Test
    void serviceListOverlaysLiveUsageCounts() {
        var service = new DictionaryService(new InMemoryDictionaryStore(), counter);
        var specialties = service.list("SPECIALTY", null, null, null);
        assertThat(specialties.stream().filter(item -> item.name().equals("机械")).findFirst()
                .orElseThrow().usageCount()).isEqualTo(5);
        assertThat(specialties.stream().filter(item -> item.name().equals("液压")).findFirst()
                .orElseThrow().usageCount()).isZero();
    }
}
