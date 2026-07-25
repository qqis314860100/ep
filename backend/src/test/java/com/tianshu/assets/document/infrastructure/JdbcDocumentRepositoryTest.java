package com.tianshu.assets.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.document.application.DocumentStateConflictException;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcDocumentRepositoryTest {

    private JdbcDocumentRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:documents;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE knowledge_document (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    document_number VARCHAR(64) NOT NULL UNIQUE,
                    title VARCHAR(200) NOT NULL,
                    summary VARCHAR(1000) NOT NULL,
                    category_code VARCHAR(64) NOT NULL,
                    maintainer_id VARCHAR(64) NOT NULL,
                    maintainer_name VARCHAR(100) NOT NULL,
                    maintainer_department VARCHAR(100) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    current_version_id BIGINT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    updated_at TIMESTAMP(3) NOT NULL,
                    version BIGINT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE document_version (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    version_number VARCHAR(40) NOT NULL,
                    change_summary VARCHAR(1000) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_by VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    published_by VARCHAR(100) NOT NULL,
                    published_at TIMESTAMP(3) NULL,
                    UNIQUE(document_id, version_number)
                )
                """);
        jdbc.execute("""
                CREATE TABLE document_file (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    version_id BIGINT NOT NULL,
                    original_name VARCHAR(1000) NOT NULL,
                    format VARCHAR(40) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    previewable BOOLEAN NOT NULL,
                    storage_key VARCHAR(500) NOT NULL,
                    content_sha256 CHAR(64) NOT NULL
                )
                """);
        repository = new JdbcDocumentRepository(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void savesAndReloadsTheWholeDraftAggregate() {
        var saved = repository.save(draft(""));

        assertThat(saved.documentNumber()).matches("DOC-\\d{6}");
        assertThat(saved.currentVersion().documentId()).isEqualTo(saved.id());
        assertThat(saved.currentVersion().files()).hasSize(2).allMatch(file -> file.id() > 0);
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void publishesWithOptimisticLockAndSearchesTheCurrentVersion() {
        var draft = repository.save(draft("DOC-JDBC-0001"));
        var publishedVersion = new DocumentVersion(draft.currentVersion().id(), draft.id(), "V1.0", "首次发布",
                DocumentVersionStatus.PUBLISHED, draft.currentVersion().files(), "陈工", draft.createdAt(),
                "陈工", Instant.parse("2026-07-26T01:00:00Z"));
        var published = new KnowledgeDocument(draft.id(), draft.documentNumber(), draft.title(), draft.summary(),
                draft.categoryCode(), draft.maintainerId(), draft.maintainerName(), draft.maintainerDepartment(),
                DocumentStatus.PUBLISHED, publishedVersion.id(), publishedVersion, draft.createdAt(),
                publishedVersion.publishedAt(), 1);

        var updated = repository.update(published, 0);

        assertThat(updated.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(repository.searchPublished(new DocumentSearchCriteria("jdbc", "WORK_INSTRUCTION", 1, 20)).total())
                .isEqualTo(1);
        assertThatThrownBy(() -> repository.update(published, 0))
                .isInstanceOf(DocumentStateConflictException.class);
    }

    private KnowledgeDocument draft(String number) {
        var now = Instant.parse("2026-07-26T00:00:00Z");
        var files = List.of(
                new DocumentFile(0, "jdbc.pdf", "PDF", 120, true, "key-pdf", "a".repeat(64)),
                new DocumentFile(0, "source.docx", "DOCX", 240, false, "key-docx", "b".repeat(64)));
        var version = new DocumentVersion(0, 0, "V1.0", "首次发布", DocumentVersionStatus.DRAFT,
                files, "陈工", now, "", null);
        return new KnowledgeDocument(0, number, "JDBC 作业指导书", "验证聚合持久化。", "WORK_INSTRUCTION",
                "u-100", "陈工", "设备工程部", DocumentStatus.DRAFT, null, version, now, now, 0);
    }
}
