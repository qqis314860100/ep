package com.tianshu.assets.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
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
}
