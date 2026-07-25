package com.tianshu.assets.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class InMemoryDocumentRepositoryTest {

    @Test
    void searchesOnlyPublishedDocumentsAcrossMetadataAndFiles() {
        var repository = new InMemoryDocumentRepository();

        var page = repository.searchPublished(new DocumentSearchCriteria("焊接", "", 1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).allMatch(document -> document.status() == DocumentStatus.PUBLISHED);
        assertThat(page.items().getFirst().title()).contains("焊接");

        var filenamePage = repository.searchPublished(new DocumentSearchCriteria("acceptance-checklist", "", 1, 20));
        assertThat(filenamePage.total()).isEqualTo(1);
    }

    @Test
    void filtersByCategoryAndKeepsStablePaging() {
        var repository = new InMemoryDocumentRepository();

        var page = repository.searchPublished(new DocumentSearchCriteria("", "TECHNICAL_SPECIFICATION", 1, 1));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().categoryCode()).isEqualTo("TECHNICAL_SPECIFICATION");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.perPage()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void treatsDocumentNumbersAsCaseInsensitiveUniqueValues() {
        var repository = new InMemoryDocumentRepository();

        assertThat(repository.existsByDocumentNumber("doc-wi-000001")).isTrue();
        assertThat(repository.existsByDocumentNumber("DOC-NOT-FOUND")).isFalse();
    }

    @Test
    void seedsACompletePdfForBrowserPreview() {
        var storage = new InMemoryFileStorage();
        var repository = new InMemoryDocumentRepository(storage);
        var file = repository.findById(101).orElseThrow().currentVersion().files().getFirst();
        var content = storage.open(file.storageKey()).orElseThrow().content();
        var pdf = new String(content, StandardCharsets.ISO_8859_1);

        assertThat(content.length).isGreaterThan(1_000);
        assertThat(pdf).startsWith("%PDF-").contains("%%EOF");
    }

    @Test
    void seedsAVisibleImageForBrowserPreview() throws Exception {
        var storage = new InMemoryFileStorage();
        var repository = new InMemoryDocumentRepository(storage);
        var file = repository.findById(102).orElseThrow().currentVersion().files().getFirst();
        var content = storage.open(file.storageKey()).orElseThrow().content();
        var image = ImageIO.read(new ByteArrayInputStream(content));

        assertThat(image.getWidth()).isEqualTo(320);
        assertThat(image.getHeight()).isEqualTo(180);
    }
}
