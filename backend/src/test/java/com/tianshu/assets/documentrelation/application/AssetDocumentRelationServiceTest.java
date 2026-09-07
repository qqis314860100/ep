package com.tianshu.assets.documentrelation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import com.tianshu.assets.documentrelation.infrastructure.InMemoryAssetDocumentRelationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetDocumentRelationServiceTest {

    private AssetDocumentRelationService service;
    private InMemoryAssetRepository assetRepository;

    @BeforeEach
    void setUp() {
        assetRepository = new InMemoryAssetRepository();
        service = new AssetDocumentRelationService(new InMemoryAssetDocumentRelationRepository(),
                assetRepository, new InMemoryDocumentRepository(new InMemoryFileStorage()));
    }

    @Test
    void hidesDraftAssetsFromByDocument() {
        var draft = assetRepository.save(new Asset(
                0, "DM-DRAFT-0001", "草稿资产", "仅用于可见性测试",
                AssetType.MIXED_ASSET, AssetStatus.DRAFT, List.of("机械"), List.of(), List.of(),
                false, List.of(), "", List.of(), List.of(),
                "陈工", "设备工程部", Instant.now(), false));
        var published = service.create(101, 101, AssetDocumentRelationType.COMPANION, "u-100");
        var fromDraft = service.create(draft.id(), 101, AssetDocumentRelationType.COMPANION, "u-100");

        assertThat(service.byDocument(101)).contains(published).doesNotContain(fromDraft);
    }

    @Test
    void createsChangesAndSoftDeletesAssetDocumentRelations() {
        var created = service.create(101, 101, AssetDocumentRelationType.COMPANION, "u-100");

        assertThat(service.byAsset(101)).containsExactly(created);
        var changed = service.changeType(created.id(), AssetDocumentRelationType.APPLICABLE, "u-100", created.version());
        assertThat(changed.relationType()).isEqualTo(AssetDocumentRelationType.APPLICABLE);

        service.remove(changed.id(), "u-100", changed.version());

        assertThat(service.byAsset(101)).isEmpty();
        assertThat(service.document(101).documentNumber()).isEqualTo("DOC-WI-000001");
    }

    @Test
    void rejectsDuplicateActiveRelations() {
        service.create(101, 101, AssetDocumentRelationType.COMPANION, "u-100");

        assertThatThrownBy(() -> service.create(101, 101, AssetDocumentRelationType.COMPANION, "u-100"))
                .isInstanceOf(AssetDocumentRelationConflictException.class);
    }
}
