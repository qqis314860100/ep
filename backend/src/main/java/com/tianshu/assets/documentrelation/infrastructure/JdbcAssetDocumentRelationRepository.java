package com.tianshu.assets.documentrelation.infrastructure;

import com.tianshu.assets.documentrelation.application.AssetDocumentRelationConflictException;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelation;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationRepository;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@Profile("local")
public class JdbcAssetDocumentRelationRepository implements AssetDocumentRelationRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcAssetDocumentRelationRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<AssetDocumentRelation> findActiveByAssetId(long assetId) {
        return query("WHERE drawing_id = ? AND deleted_at IS NULL ORDER BY created_at DESC", assetId);
    }

    @Override
    public List<AssetDocumentRelation> findActiveByDocumentId(long documentId) {
        return query("WHERE document_id = ? AND deleted_at IS NULL ORDER BY created_at DESC", documentId);
    }

    @Override
    public Optional<AssetDocumentRelation> findById(long id) {
        return query("WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public Optional<AssetDocumentRelation> findAny(long assetId, long documentId, AssetDocumentRelationType type) {
        return query("WHERE drawing_id = ? AND document_id = ? AND relation_type = ?", assetId, documentId, type.name())
                .stream().findFirst();
    }

    @Override
    public AssetDocumentRelation save(AssetDocumentRelation relation, String action, String operator) {
        return transactions.execute(status -> {
            var keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO asset_document_relation (drawing_id, document_id, relation_type, created_by,
                            created_at, version) VALUES (?, ?, ?, ?, ?, ?)
                        """, new String[] {"id"});
                statement.setLong(1, relation.assetId());
                statement.setLong(2, relation.documentId());
                statement.setString(3, relation.relationType().name());
                statement.setString(4, relation.createdBy());
                statement.setTimestamp(5, Timestamp.from(relation.createdAt()));
                statement.setLong(6, relation.version());
                return statement;
            }, keys);
            var id = keys.getKey();
            if (id == null) throw new IllegalStateException("数据库未返回关联关系 ID");
            writeAudit(id.longValue(), action, null, relation.relationType().name(), operator, relation.createdAt());
            return findById(id.longValue()).orElseThrow();
        });
    }

    @Override
    public AssetDocumentRelation update(AssetDocumentRelation relation, String action, String operator,
            long expectedVersion) {
        return transactions.execute(status -> {
            var previous = findById(relation.id()).orElseThrow(() -> new IllegalArgumentException("关联关系不存在或不可访问"));
            var count = jdbc.update("""
                    UPDATE asset_document_relation SET relation_type = ?, updated_by = ?, updated_at = ?,
                        deleted_by = ?, deleted_at = ?, version = ? WHERE id = ? AND version = ?
                    """, relation.relationType().name(), relation.updatedBy(), timestamp(relation.updatedAt()),
                    relation.deletedBy(), timestamp(relation.deletedAt()), relation.version(), relation.id(), expectedVersion);
            if (count != 1) throw new AssetDocumentRelationConflictException("关联关系已被其他用户修改，请刷新后重试");
            writeAudit(relation.id(), action, previous.relationType().name(), relation.relationType().name(), operator,
                    relation.updatedAt());
            return findById(relation.id()).orElseThrow();
        });
    }

    private List<AssetDocumentRelation> query(String where, Object... values) {
        return jdbc.query("SELECT id, drawing_id, document_id, relation_type, created_by, created_at, updated_by,"
                + " updated_at, deleted_by, deleted_at, version FROM asset_document_relation " + where,
                (rs, row) -> new AssetDocumentRelation(rs.getLong("id"), rs.getLong("drawing_id"),
                        rs.getLong("document_id"), AssetDocumentRelationType.valueOf(rs.getString("relation_type")),
                        rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(), rs.getString("updated_by"),
                        instant(rs.getTimestamp("updated_at")), rs.getString("deleted_by"),
                        instant(rs.getTimestamp("deleted_at")), rs.getLong("version")), values);
    }

    private void writeAudit(long relationId, String action, String before, String after, String operator,
            java.time.Instant operatedAt) {
        jdbc.update("""
                INSERT INTO asset_document_relation_audit (relation_id, action, before_value_json, after_value_json,
                    operated_by, operated_at) VALUES (?, ?, ?, ?, ?, ?)
                """, relationId, action, json(before), json(after), operator, Timestamp.from(operatedAt));
    }

    private String json(String value) { return value == null ? null : "{\"relationType\":\"" + value + "\"}"; }
    private Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
    private java.time.Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
