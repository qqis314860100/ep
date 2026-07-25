package com.tianshu.assets.document.infrastructure;

import com.tianshu.assets.document.application.DocumentStateConflictException;
import com.tianshu.assets.document.application.DuplicateDocumentNumberException;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentPage;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@Profile("local")
public class JdbcDocumentRepository implements DocumentRepository {

    protected final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public DocumentPage searchPublished(DocumentSearchCriteria criteria) {
        var where = new StringBuilder(" WHERE d.status = 'PUBLISHED'");
        var arguments = new ArrayList<Object>();
        if (!criteria.categoryCode().isBlank()) {
            where.append(" AND d.category_code = ?");
            arguments.add(criteria.categoryCode());
        }
        if (!criteria.query().isBlank()) {
            var like = "%" + criteria.query().toLowerCase() + "%";
            where.append("""
                     AND (LOWER(d.document_number) LIKE ? OR LOWER(d.title) LIKE ? OR LOWER(d.summary) LIKE ?
                          OR LOWER(d.maintainer_name) LIKE ? OR EXISTS (
                              SELECT 1 FROM document_file df
                              WHERE df.document_id = d.id AND LOWER(df.original_name) LIKE ?))
                    """);
            arguments.addAll(List.of(like, like, like, like, like));
        }
        var total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_document d" + where,
                Long.class, arguments.toArray());
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(criteria.perPage());
        pageArguments.add((criteria.page() - 1) * criteria.perPage());
        var ids = jdbcTemplate.queryForList("SELECT d.id FROM knowledge_document d" + where
                + " ORDER BY d.updated_at DESC, d.id DESC LIMIT ? OFFSET ?", Long.class, pageArguments.toArray());
        var items = ids.stream().map(this::findRequired).toList();
        return new DocumentPage(items, total == null ? 0 : total, criteria.page(), criteria.perPage());
    }

    @Override
    public Optional<KnowledgeDocument> findById(long id) {
        var rows = jdbcTemplate.query("""
                SELECT id, document_number, title, summary, category_code, maintainer_id, maintainer_name,
                       maintainer_department, status, current_version_id, created_at, updated_at, version
                FROM knowledge_document WHERE id = ?
                """, (resultSet, rowNumber) -> new DocumentRow(
                resultSet.getLong("id"), resultSet.getString("document_number"), resultSet.getString("title"),
                resultSet.getString("summary"), resultSet.getString("category_code"),
                resultSet.getString("maintainer_id"), resultSet.getString("maintainer_name"),
                resultSet.getString("maintainer_department"), DocumentStatus.valueOf(resultSet.getString("status")),
                nullableLong(resultSet, "current_version_id"), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("version")), id);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        var documentVersion = findVersion(row.id(), row.currentVersionId());
        return Optional.of(new KnowledgeDocument(row.id(), row.documentNumber(), row.title(), row.summary(),
                row.categoryCode(), row.maintainerId(), row.maintainerName(), row.maintainerDepartment(), row.status(),
                row.currentVersionId(), documentVersion, row.createdAt(), row.updatedAt(), row.version()));
    }

    @Override
    public boolean existsByDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) return false;
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE LOWER(document_number) = LOWER(?)",
                Long.class, documentNumber.trim());
        return count != null && count > 0;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        try {
            return transactionTemplate.execute(status -> saveAggregate(document));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateDocumentNumberException("文档编号已存在：" + document.documentNumber());
        }
    }

    @Override
    public KnowledgeDocument update(KnowledgeDocument document, long expectedVersion) {
        var updated = transactionTemplate.execute(status -> {
            var count = jdbcTemplate.update("""
                    UPDATE knowledge_document
                    SET document_number = ?, title = ?, summary = ?, category_code = ?, maintainer_id = ?,
                        maintainer_name = ?, maintainer_department = ?, status = ?, current_version_id = ?,
                        updated_at = ?, version = ?
                    WHERE id = ? AND version = ? AND status = 'DRAFT'
                    """, document.documentNumber(), document.title(), document.summary(), document.categoryCode(),
                    document.maintainerId(), document.maintainerName(), document.maintainerDepartment(),
                    document.status().name(), document.currentVersionId(), Timestamp.from(document.updatedAt()),
                    document.version(), document.id(), expectedVersion);
            if (count != 1) {
                throw new DocumentStateConflictException("文档已被其他用户更新或不再是草稿");
            }
            var version = document.currentVersion();
            var versionCount = jdbcTemplate.update("""
                    UPDATE document_version
                    SET version_number = ?, change_summary = ?, status = ?, published_by = ?, published_at = ?
                    WHERE id = ? AND document_id = ? AND status = 'DRAFT'
                    """, version.versionNumber(), version.changeSummary(), version.status().name(),
                    version.publishedBy(), timestamp(version.publishedAt()), version.id(), document.id());
            if (versionCount != 1) {
                throw new DocumentStateConflictException("文档版本已被其他用户更新");
            }
            return findRequired(document.id());
        });
        if (updated == null) throw new DocumentStateConflictException("文档更新事务未完成");
        return updated;
    }

    private KnowledgeDocument saveAggregate(KnowledgeDocument document) {
        var initialNumber = document.documentNumber().isBlank()
                ? "PENDING-" + UUID.randomUUID()
                : document.documentNumber();
        var documentId = insertAndReturnKey("""
                INSERT INTO knowledge_document
                    (document_number, title, summary, category_code, maintainer_id, maintainer_name,
                     maintainer_department, status, current_version_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """, initialNumber, document.title(), document.summary(), document.categoryCode(),
                document.maintainerId(), document.maintainerName(), document.maintainerDepartment(),
                document.status().name(), Timestamp.from(document.createdAt()), Timestamp.from(document.updatedAt()),
                document.version());
        var number = document.documentNumber().isBlank() ? "DOC-%06d".formatted(documentId) : document.documentNumber();
        if (!number.equals(initialNumber)) {
            jdbcTemplate.update("UPDATE knowledge_document SET document_number = ? WHERE id = ?", number, documentId);
        }
        var version = document.currentVersion();
        var versionId = insertAndReturnKey("""
                INSERT INTO document_version
                    (document_id, version_number, change_summary, status, created_by, created_at,
                     published_by, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, documentId, version.versionNumber(), version.changeSummary(), version.status().name(),
                version.createdBy(), Timestamp.from(version.createdAt()), version.publishedBy(),
                timestamp(version.publishedAt()));
        for (var file : version.files()) {
            jdbcTemplate.update("""
                    INSERT INTO document_file
                        (document_id, version_id, original_name, format, size_bytes, previewable,
                         storage_key, content_sha256)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, documentId, versionId, file.name(), file.format(), file.sizeBytes(), file.previewable(),
                    file.storageKey(), file.contentSha256());
        }
        if (document.status() == DocumentStatus.PUBLISHED) {
            jdbcTemplate.update("UPDATE knowledge_document SET current_version_id = ? WHERE id = ?", versionId, documentId);
        }
        return findRequired(documentId);
    }

    private DocumentVersion findVersion(long documentId, Long currentVersionId) {
        var sql = currentVersionId == null
                ? """
                  SELECT id, document_id, version_number, change_summary, status, created_by, created_at,
                         published_by, published_at
                  FROM document_version WHERE document_id = ? ORDER BY id DESC LIMIT 1
                  """
                : """
                  SELECT id, document_id, version_number, change_summary, status, created_by, created_at,
                         published_by, published_at
                  FROM document_version WHERE document_id = ? AND id = ?
                  """;
        var arguments = currentVersionId == null ? new Object[] {documentId} : new Object[] {documentId, currentVersionId};
        var versions = jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            var versionId = resultSet.getLong("id");
            return new DocumentVersion(versionId, resultSet.getLong("document_id"),
                    resultSet.getString("version_number"), resultSet.getString("change_summary"),
                    DocumentVersionStatus.valueOf(resultSet.getString("status")), findFiles(documentId, versionId),
                    resultSet.getString("created_by"), resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getString("published_by"), instant(resultSet.getTimestamp("published_at")));
        }, arguments);
        if (versions.isEmpty()) throw new IllegalStateException("文档缺少版本数据：" + documentId);
        return versions.getFirst();
    }

    private List<DocumentFile> findFiles(long documentId, long versionId) {
        return jdbcTemplate.query("""
                SELECT id, original_name, format, size_bytes, previewable, storage_key, content_sha256
                FROM document_file WHERE document_id = ? AND version_id = ? ORDER BY id
                """, (resultSet, rowNumber) -> new DocumentFile(resultSet.getLong("id"),
                resultSet.getString("original_name"), resultSet.getString("format"), resultSet.getLong("size_bytes"),
                resultSet.getBoolean("previewable"), resultSet.getString("storage_key"),
                resultSet.getString("content_sha256")), documentId, versionId);
    }

    private long insertAndReturnKey(String sql, Object... arguments) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (var index = 0; index < arguments.length; index++) {
                var value = arguments[index];
                if (value == null) statement.setNull(index + 1, Types.TIMESTAMP);
                else statement.setObject(index + 1, value);
            }
            return statement;
        }, keyHolder);
        var key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("数据库未返回新增记录 ID");
        return key.longValue();
    }

    private KnowledgeDocument findRequired(long id) {
        return findById(id).orElseThrow(() -> new IllegalStateException("文档聚合写入后无法读取：" + id));
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        var value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record DocumentRow(long id, String documentNumber, String title, String summary, String categoryCode,
            String maintainerId, String maintainerName, String maintainerDepartment, DocumentStatus status,
            Long currentVersionId, java.time.Instant createdAt, java.time.Instant updatedAt, long version) {}
}
