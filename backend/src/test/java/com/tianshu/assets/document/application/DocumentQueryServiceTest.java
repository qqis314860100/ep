package com.tianshu.assets.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentQueryServiceTest {

    private DocumentQueryService queries;

    @BeforeEach
    void setUp() {
        var storage = new InMemoryFileStorage();
        queries = new DocumentQueryService(new InMemoryDocumentRepository(storage), storage);
    }

    @Test
    void returnsOnlyPublishedDocumentsAndPublishedDetails() {
        var page = queries.search(new DocumentSearchCriteria("", "", 1, 20));

        assertThat(page.items()).hasSize(2).allMatch(document -> document.status() == DocumentStatus.PUBLISHED);
        assertThat(queries.getPublished(101).id()).isEqualTo(101);
        assertThatThrownBy(() -> queries.getPublished(103)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void opensOnlyFilesBelongingToTheRequestedCurrentVersion() {
        var document = queries.getPublished(101);
        var file = document.currentVersion().files().getFirst();

        var access = queries.openPublishedFile(document.id(), document.currentVersion().id(), file.id());

        assertThat(access.document().status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(access.storedFile().content()).isNotEmpty();
        assertThatThrownBy(() -> queries.openPublishedFile(document.id(), document.currentVersion().id(), 9999))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void servesAValidPngSignatureForThePreviewSeed() {
        var document = queries.getPublished(102);
        var file = document.currentVersion().files().getFirst();

        var content = queries.openPublishedFile(document.id(), document.currentVersion().id(), file.id())
                .storedFile().content();

        assertThat(content).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }
}
