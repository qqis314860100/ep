package com.tianshu.assets.documentrelation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import com.tianshu.assets.documentrelation.infrastructure.InMemoryAssetDocumentRelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetDocumentRelationServiceTest {

    private AssetDocumentRelationService service;

    @BeforeEach
    void setUp() {
        service = new AssetDocumentRelationService(new InMemoryAssetDocumentRelationRepository(),
                new InMemoryAssetRepository(), new InMemoryDocumentRepository(new InMemoryFileStorage()));
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
