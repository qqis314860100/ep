package com.tianshu.assets.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import org.junit.jupiter.api.Test;

class AssetQueryServiceTest {

    private final AssetQueryService service = new AssetQueryService(new InMemoryAssetRepository());

    @Test
    void searchesAssetsByKeyword() {
        var result = service.search(new AssetSearchCriteria("焊接", null, null, null, null, 1, 20));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting("assetNumber")
                .containsExactly("DM-ND-A-0001", "DM-ND-A-0002", "DM-ND-A-0003");
    }

    @Test
    void requiresBaseAndLineToMatchTheSameScope() {
        var result = service.search(new AssetSearchCriteria("", null, null, "宁德基地", "B 拉线", 1, 20));

        assertThat(result.total()).isZero();
    }

    @Test
    void returnsRelationsForExistingAsset() {
        assertThat(service.getRelations(101)).hasSize(2);
    }

    @Test
    void rejectsUnknownAsset() {
        assertThatThrownBy(() -> service.get(9999)).isInstanceOf(AssetNotFoundException.class);
    }
}
