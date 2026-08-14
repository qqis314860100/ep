package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetPage;
import com.tianshu.assets.asset.domain.AssetRelation;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.asset.domain.RelationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Profile({"oceanbase", "local"})
public class OceanBaseAssetRepository implements AssetRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, drawing_title, drawing_content, drawing_url, drawing_img,
                   drawing_column, drawing_label, drawing_platform, drawing_line,
                   drawing_format, created_by_name, last_update_date
            FROM sys_drawing
            """;

    private final JdbcClient jdbcClient;
    private final OceanBaseAssetExtensionStore extensionStore;
    private final ObjectMapper objectMapper;
    private final boolean writesEnabled;

    public OceanBaseAssetRepository(JdbcClient jdbcClient, OceanBaseAssetExtensionStore extensionStore,
            ObjectMapper objectMapper, @Value("${asset.database-writes-enabled:false}") boolean writesEnabled) {
        this.jdbcClient = jdbcClient;
        this.extensionStore = extensionStore;
        this.objectMapper = objectMapper;
        this.writesEnabled = writesEnabled;
    }

    @Override
    public AssetPage search(AssetSearchCriteria criteria) {
        var where = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();

        if (!criteria.query().isBlank()) {
            where.add("(drawing_title LIKE :query OR drawing_content LIKE :query OR drawing_label LIKE :query)");
            parameters.put("query", "%" + criteria.query() + "%");
        }
        if (criteria.assetType() != null && !extensionStore.enabled()) {
            where.add(assetTypePredicate(criteria.assetType()));
        } else if (criteria.assetType() != null) {
            where.add("EXISTS (SELECT 1 FROM asset_package_ext ap WHERE ap.drawing_id = sys_drawing.id AND ap.asset_type = :assetType)");
            parameters.put("assetType", criteria.assetType().name());
        }
        if (criteria.status() != null && !extensionStore.enabled() && criteria.status() != AssetStatus.PENDING_CURATION) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        } else if (criteria.status() != null && extensionStore.enabled()) {
            where.add("EXISTS (SELECT 1 FROM asset_package_ext ap WHERE ap.drawing_id = sys_drawing.id AND ap.status = :status)");
            parameters.put("status", criteria.status().name());
        }
        if (!extensionStore.enabled() && criteria.platformFamily() != null && !criteria.platformFamily().isBlank()) {
            where.add("drawing_platform = :platformFamily");
            parameters.put("platformFamily", criteria.platformFamily());
        }
        if (criteria.ownerName() != null && !criteria.ownerName().isBlank()) {
            where.add("created_by_name = :ownerName");
            parameters.put("ownerName", criteria.ownerName());
        }
        if (!extensionStore.enabled() && criteria.platformVariant() != null && !criteria.platformVariant().isBlank()) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }
        if (!extensionStore.enabled() && (hasText(criteria.productLine()) || hasText(criteria.processSection()))) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }
        if (!extensionStore.enabled() && criteria.productionLine() != null && !criteria.productionLine().isBlank()) {
            where.add("drawing_line = :productionLine");
            parameters.put("productionLine", criteria.productionLine());
        }
        // Legacy rows do not contain a reliable base field, so a base filter cannot match safely.
        if (!extensionStore.enabled() && criteria.base() != null && !criteria.base().isBlank()) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }
        if (extensionStore.enabled() && hasScopeCriteria(criteria)) {
            var scopePredicates = new ArrayList<String>();
            if (hasText(criteria.platformFamily())) {
                scopePredicates.add("scope.platform_family = :platformFamily");
                parameters.put("platformFamily", criteria.platformFamily());
            }
            if (hasText(criteria.platformVariant())) {
                scopePredicates.add("scope.platform_variant = :platformVariant");
                parameters.put("platformVariant", criteria.platformVariant());
            }
            if (hasText(criteria.productLine())) {
                scopePredicates.add("scope.product_line = :productLine");
                parameters.put("productLine", criteria.productLine());
            }
            if (hasText(criteria.base())) {
                scopePredicates.add("scope.base_name = :base");
                parameters.put("base", criteria.base());
            }
            if (hasText(criteria.productionLine())) {
                scopePredicates.add("scope.production_line = :productionLine");
                parameters.put("productionLine", criteria.productionLine());
            }
            if (hasText(criteria.processSection())) {
                scopePredicates.add("scope.process_section = :processSection");
                parameters.put("processSection", criteria.processSection());
            }
            where.add("EXISTS (SELECT 1 FROM asset_scope_ext scope WHERE scope.drawing_id = sys_drawing.id AND "
                    + String.join(" AND ", scopePredicates) + ")");
        }
        if (Boolean.TRUE.equals(criteria.previewable()) && extensionStore.enabled()) {
            where.add("EXISTS (SELECT 1 FROM asset_file_ext af WHERE af.drawing_id = sys_drawing.id AND af.previewable = 1 AND af.file_status = 'AVAILABLE')");
        } else if (Boolean.TRUE.equals(criteria.previewable())) {
            where.add("((drawing_img IS NOT NULL AND drawing_img <> '') OR UPPER(drawing_format) IN ('PDF','PNG','JPG','JPEG','TIFF'))");
        }
        if (hasText(criteria.specialty())) {
            where.add("drawing_column LIKE :specialty");
            parameters.put("specialty", "%" + criteria.specialty() + "%");
        }
        if (hasText(criteria.fileFormat())) {
            where.add("UPPER(drawing_format) = :fileFormat");
            parameters.put("fileFormat", criteria.fileFormat());
        }
        if (criteria.updatedFrom() != null) {
            where.add("last_update_date >= :updatedFrom");
            parameters.put("updatedFrom", Timestamp.from(criteria.updatedFrom()));
        }
        if (criteria.updatedTo() != null) {
            where.add("last_update_date <= :updatedTo");
            parameters.put("updatedTo", Timestamp.from(criteria.updatedTo()));
        }
        if (criteria.wantsMissingScope() && extensionStore.enabled()) {
            where.add("NOT EXISTS (SELECT 1 FROM asset_scope_ext s WHERE s.drawing_id = sys_drawing.id "
                    + "AND s.platform_family IS NOT NULL AND s.platform_family <> '' "
                    + "AND s.platform_variant IS NOT NULL AND s.platform_variant <> '' "
                    + "AND s.product_line IS NOT NULL AND s.product_line <> '' "
                    + "AND s.base_name IS NOT NULL AND s.base_name <> '' "
                    + "AND s.production_line IS NOT NULL AND s.production_line <> '' "
                    + "AND s.process_section IS NOT NULL AND s.process_section <> '')");
        }

        var whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        var total = jdbcClient.sql("SELECT COUNT(*) FROM sys_drawing" + whereClause)
                .params(parameters)
                .query(Long.class)
                .single();

        parameters.put("limit", criteria.perPage());
        parameters.put("offset", (criteria.page() - 1) * criteria.perPage());
        var orderBy = switch (criteria.sort()) {
            case "NAME" -> "drawing_title ASC, id DESC";
            case "ASSET_NUMBER" -> extensionStore.enabled()
                    ? "(SELECT ap.asset_number FROM asset_package_ext ap WHERE ap.drawing_id = sys_drawing.id) ASC, id DESC"
                    : "drawing_title ASC, id DESC";
            default -> "last_update_date DESC, id DESC";
        };
        var items = jdbcClient.sql(SELECT_COLUMNS + whereClause + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset")
                .params(parameters)
                .query(this::mapAsset)
                .list();
        return new AssetPage(items, total, criteria.page(), criteria.perPage());
    }

    @Override
    public Optional<Asset> findById(long id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapAsset)
                .optional();
    }

    @Override
    public List<AssetRelation> findRelations(long assetId) {
        if (!extensionStore.enabled()) return List.of();
        return jdbcClient.sql("""
                SELECT link.id, link.source_drawing_id, link.target_drawing_id, link.link_type, link.description,
                       other.drawing_title, other.drawing_platform, other.drawing_line,
                       package.asset_number, package.asset_type, package.status
                FROM asset_module_link_ext link
                JOIN sys_drawing other ON other.id = CASE WHEN link.target_drawing_id = :assetId
                        THEN link.source_drawing_id ELSE link.target_drawing_id END
                LEFT JOIN asset_package_ext package ON package.drawing_id = other.id
                WHERE (link.source_drawing_id = :assetId OR link.target_drawing_id = :assetId)
                  AND link.deleted_at IS NULL
                ORDER BY link.id
                """).param("assetId", assetId).query((rs, row) -> {
                    var fromSource = rs.getLong("source_drawing_id") == assetId;
                    var type = enumValue(AssetType.class, rs.getString("asset_type"), AssetType.OTHER);
                    var status = enumValue(AssetStatus.class, rs.getString("status"), AssetStatus.PENDING_CURATION);
                    var relationType = "MODULE_REFERENCE".equals(rs.getString("link_type"))
                            ? RelationType.REFERENCES
                            : enumValue(RelationType.class, rs.getString("link_type"), RelationType.ASSOCIATED_WITH);
                    return new AssetRelation(rs.getLong("id"), rs.getLong("source_drawing_id"),
                            rs.getLong("target_drawing_id"), nullable(rs.getString("asset_number")),
                            nullable(rs.getString("drawing_title")), type, status, relationType,
                            relationLabel(relationType, fromSource),
                            nullable(rs.getString("drawing_platform")) + " / " + nullable(rs.getString("drawing_line")),
                            nullable(rs.getString("description")), "", Instant.EPOCH, "", Instant.EPOCH, 0);
                }).list();
    }

    @Override
    public List<AssetRelation> findAllRelations() {
        if (!extensionStore.enabled()) return List.of();
        return jdbcClient.sql("""
                SELECT link.id, link.source_drawing_id, link.target_drawing_id, link.link_type, link.description,
                       other.drawing_title, other.drawing_platform, other.drawing_line,
                       package.asset_number, package.asset_type, package.status
                FROM asset_module_link_ext link
                JOIN sys_drawing other ON other.id = link.target_drawing_id
                LEFT JOIN asset_package_ext package ON package.drawing_id = other.id
                WHERE link.deleted_at IS NULL
                ORDER BY link.id
                """).query((rs, row) -> {
                    var type = enumValue(AssetType.class, rs.getString("asset_type"), AssetType.OTHER);
                    var status = enumValue(AssetStatus.class, rs.getString("status"), AssetStatus.PENDING_CURATION);
                    var relationType = "MODULE_REFERENCE".equals(rs.getString("link_type"))
                            ? RelationType.REFERENCES
                            : enumValue(RelationType.class, rs.getString("link_type"), RelationType.ASSOCIATED_WITH);
                    return new AssetRelation(rs.getLong("id"), rs.getLong("source_drawing_id"),
                            rs.getLong("target_drawing_id"), nullable(rs.getString("asset_number")),
                            nullable(rs.getString("drawing_title")), type, status, relationType,
                            relationLabel(relationType, true),
                            nullable(rs.getString("drawing_platform")) + " / " + nullable(rs.getString("drawing_line")),
                            nullable(rs.getString("description")), "", Instant.EPOCH, "", Instant.EPOCH, 0);
                }).list();
    }

    @Override
    public Optional<AssetRelation> findRelationById(long relationId) {
        if (!extensionStore.enabled()) return Optional.empty();
        return findAllRelations().stream().filter(relation -> relation.id() == relationId).findFirst();
    }

    @Override
    @Transactional
    public AssetRelation createRelation(AssetRelation relation) {
        requireWritesEnabled();
        jdbcClient.sql("""
                INSERT INTO asset_module_link_ext (source_drawing_id, target_drawing_id, link_type, description)
                VALUES (:source, :target, :type, :description)
                """).param("source", relation.sourceAssetId()).param("target", relation.targetAssetId())
                .param("type", relation.relationType().name()).param("description", relation.description())
                .update();
        var id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return findRelationById(id).orElseThrow(() -> new IllegalStateException("关系保存后无法读取"));
    }

    @Override
    @Transactional
    public AssetRelation updateRelation(AssetRelation relation, long expectedVersion) {
        requireWritesEnabled();
        var updated = jdbcClient.sql("""
                UPDATE asset_module_link_ext
                SET source_drawing_id = :source, target_drawing_id = :target,
                    link_type = :type, description = :description
                WHERE id = :id AND deleted_at IS NULL
                """).param("source", relation.sourceAssetId()).param("target", relation.targetAssetId())
                .param("type", relation.relationType().name()).param("description", relation.description())
                .param("id", relation.id())
                .update();
        if (updated != 1) throw new IllegalStateException("资产关系不存在：" + relation.id());
        return findRelationById(relation.id()).orElseThrow();
    }

    @Override
    @Transactional
    public void removeRelation(long relationId) {
        requireWritesEnabled();
        var removed = jdbcClient.sql("""
                UPDATE asset_module_link_ext SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = :id AND deleted_at IS NULL
                """).param("id", relationId).update();
        if (removed != 1) throw new IllegalStateException("资产关系不存在：" + relationId);
    }

    private String relationLabel(RelationType relationType, boolean fromSource) {
        return switch (relationType) {
            case CONTAINS -> fromSource ? "包含" : "属于";
            case REFERENCES -> fromSource ? "引用" : "被引用";
            case REPLACES -> fromSource ? "替代" : "被替代";
            case MATCHES -> "配套";
            case ASSOCIATED_WITH -> "关联";
        };
    }

    @Override
    @Transactional
    public Asset save(Asset asset) {
        requireWritesEnabled();
        jdbcClient.sql("""
                INSERT INTO sys_drawing
                    (drawing_title, drawing_content, drawing_url, drawing_column, drawing_label,
                     drawing_platform, drawing_line, drawing_format, created_by_name, last_updated_by_name)
                VALUES (:name, :description, :sourceFile, :specialties, :tags, :platform, :line, :format,
                        :ownerName, :ownerName)
                """).param("name", asset.name()).param("description", asset.description())
                .param("sourceFile", primaryFileName(asset)).param("specialties", json(asset.specialties()))
                .param("tags", json(asset.tags())).param("platform", firstScopeValue(asset, ScopeValue.PLATFORM))
                .param("line", firstScopeValue(asset, ScopeValue.LINE)).param("format", primaryFormat(asset))
                .param("ownerName", asset.ownerName()).update();
        var id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        saveExtensionData(id, asset);
        return findById(id).orElseThrow(() -> new IllegalStateException("资产保存后无法读取"));
    }

    @Override
    @Transactional
    public Asset update(Asset asset) {
        requireWritesEnabled();
        var updated = jdbcClient.sql("""
                UPDATE asset_package_ext
                SET asset_number = :assetNumber, asset_type = :assetType, status = :status,
                    module_tags = :moduleTags, standard_equipment_module = :standardModule,
                    linked_module_asset_ids = :linkedIds, equipment_interconnect_code = :equipmentCode,
                    owner_department = :department, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE drawing_id = :id
                """).param("assetNumber", asset.assetNumber()).param("assetType", asset.assetType().name())
                .param("status", asset.status().name()).param("moduleTags", json(asset.moduleTags()))
                .param("standardModule", asset.standardEquipmentModule())
                .param("linkedIds", json(asset.linkedModuleAssetIds()))
                .param("equipmentCode", asset.equipmentInterconnectCode())
                .param("department", asset.ownerDepartment()).param("id", asset.id()).update();
        if (updated == 0) throw new IllegalArgumentException("资产不存在：" + asset.id());
        jdbcClient.sql("""
                UPDATE sys_drawing SET drawing_title = :name, drawing_content = :description,
                    drawing_column = :specialties, drawing_label = :tags, last_updated_by_name = :ownerName
                WHERE id = :id
                """).param("name", asset.name()).param("description", asset.description())
                .param("specialties", json(asset.specialties())).param("tags", json(asset.tags()))
                .param("ownerName", asset.ownerName()).param("id", asset.id()).update();
        return findById(asset.id()).orElseThrow(() -> new IllegalArgumentException("资产不存在：" + asset.id()));
    }

    @Override
    public boolean existsByAssetNumber(String assetNumber) {
        if (!extensionStore.enabled() || assetNumber == null || assetNumber.isBlank()) return false;
        return jdbcClient.sql("SELECT COUNT(*) FROM asset_package_ext WHERE asset_number = :assetNumber")
                .param("assetNumber", assetNumber).query(Long.class).single() > 0;
    }

    private void saveExtensionData(long drawingId, Asset asset) {
        jdbcClient.sql("""
                INSERT INTO asset_package_ext
                    (drawing_id, asset_number, asset_type, status, module_tags, standard_equipment_module,
                     linked_module_asset_ids, equipment_interconnect_code, owner_department)
                VALUES (:drawingId, :assetNumber, :assetType, :status, :moduleTags, :standardModule,
                        :linkedIds, :equipmentCode, :department)
                """).param("drawingId", drawingId).param("assetNumber", asset.assetNumber())
                .param("assetType", asset.assetType().name()).param("status", asset.status().name())
                .param("moduleTags", json(asset.moduleTags())).param("standardModule", asset.standardEquipmentModule())
                .param("linkedIds", json(asset.linkedModuleAssetIds()))
                .param("equipmentCode", asset.equipmentInterconnectCode()).param("department", asset.ownerDepartment())
                .update();
        for (var scope : asset.scopes()) {
            jdbcClient.sql("""
                    INSERT INTO asset_scope_ext
                        (drawing_id, platform_family, platform_variant, product_line, base_name,
                         production_line, process_section)
                    VALUES (:drawingId, :platformFamily, :platformVariant, :productLine, :baseName,
                            :productionLine, :processSection)
                    """).param("drawingId", drawingId).param("platformFamily", scope.platformFamily())
                    .param("platformVariant", emptyToNull(scope.platformVariant())).param("productLine", emptyToNull(scope.productLine()))
                    .param("baseName", emptyToNull(scope.base())).param("productionLine", emptyToNull(scope.productionLine()))
                    .param("processSection", emptyToNull(scope.processSection())).update();
        }
        for (var file : asset.files()) {
            jdbcClient.sql("""
                    INSERT INTO asset_file_ext
                        (drawing_id, original_name, display_name, format, role, storage_key, content_sha256,
                         size_bytes, previewable, is_primary)
                    VALUES (:drawingId, :name, :name, :format, :role, :storageKey, :sha256,
                            :sizeBytes, :previewable, :isPrimary)
                    """).param("drawingId", drawingId).param("name", file.name())
                    .param("format", file.format()).param("role", file.role())
                    .param("storageKey", emptyToNull(file.storageKey())).param("sha256", emptyToNull(file.contentSha256()))
                    .param("sizeBytes", file.sizeBytes()).param("previewable", file.previewable())
                    .param("isPrimary", file.primary()).update();
        }
    }

    private void requireWritesEnabled() {
        if (!writesEnabled) throw new UnsupportedOperationException("旧系统适配器为只读模式");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("资产结构化字段无法保存", exception);
        }
    }

    private String primaryFileName(Asset asset) {
        return asset.files().stream().filter(AssetFile::primary).findFirst().or(() -> asset.files().stream().findFirst())
                .map(AssetFile::name).orElse("");
    }

    private String primaryFormat(Asset asset) {
        return asset.files().stream().filter(AssetFile::primary).findFirst().or(() -> asset.files().stream().findFirst())
                .map(AssetFile::format).orElse("OTHER");
    }

    private String firstScopeValue(Asset asset, ScopeValue value) {
        return asset.scopes().stream().findFirst().map(scope -> value == ScopeValue.PLATFORM
                ? scope.platformFamily() : scope.productionLine()).orElse("");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private enum ScopeValue { PLATFORM, LINE }

    private Asset mapAsset(ResultSet resultSet, int rowNumber) throws SQLException {
        var id = resultSet.getLong("id");
        var format = nullable(resultSet.getString("drawing_format"));
        var line = nullable(resultSet.getString("drawing_line"));
        var platform = nullable(resultSet.getString("drawing_platform"));
        var sourceReference = nullable(resultSet.getString("drawing_url"));
        var legacyFiles = sourceReference.isBlank()
                ? List.<AssetFile>of()
                : List.of(new AssetFile(
                        id,
                        sourceReference,
                        format,
                        0,
                        "历史源文件",
                        isPreviewable(format),
                        true));
        var updated = resultSet.getTimestamp("last_update_date");
        var extension = extensionStore.find(id).orElse(null);
        var files = extension == null || extension.files().isEmpty() ? legacyFiles : extension.files();
        var normalizedScopes = extension == null || extension.scopes().isEmpty()
                ? List.of(new AssetScope(platform, "", "", line, "", platform, ""))
                : extension.scopes();
        return new Asset(
                id,
                extension == null || extension.assetNumber().isBlank()
                        ? "LEGACY-" + String.format(Locale.ROOT, "%012d", id)
                        : extension.assetNumber(),
                nullable(resultSet.getString("drawing_title")),
                nullable(resultSet.getString("drawing_content")),
                extension == null ? inferType(format) : extension.assetType(),
                extension == null ? AssetStatus.PENDING_CURATION : extension.status(),
                parseLegacyValues(resultSet.getString("drawing_column")),
                parseLegacyValues(resultSet.getString("drawing_label")),
                extension == null ? List.of() : extension.moduleTags(),
                extension != null && extension.standardEquipmentModule(),
                extension == null ? List.of() : extension.linkedModuleAssetIds(),
                extension == null ? "" : extension.equipmentInterconnectCode(),
                normalizedScopes,
                files,
                nullable(resultSet.getString("created_by_name")),
                extension == null ? "" : extension.ownerDepartment(),
                updated == null ? null : updated.toInstant().atOffset(ZoneOffset.UTC).toInstant(),
                extension == null);
    }

    private boolean hasScopeCriteria(AssetSearchCriteria criteria) {
        return hasText(criteria.platformFamily()) || hasText(criteria.platformVariant())
                || hasText(criteria.productLine()) || hasText(criteria.base()) || hasText(criteria.productionLine())
                || hasText(criteria.processSection());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> parseLegacyValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        var normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return java.util.Arrays.stream(normalized.split(","))
                .map(item -> item.trim().replace("\"", ""))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String assetTypePredicate(AssetType assetType) {
        return switch (assetType) {
            case THREE_DIMENSIONAL_MODEL -> "UPPER(drawing_format) IN ('X_T', 'STEP', 'STP')";
            case TWO_DIMENSIONAL_DRAWING -> "UPPER(drawing_format) IN ('PDF', 'DWG', 'DXF', 'PNG', 'JPG', 'JPEG', 'TIFF')";
            case MIXED_ASSET -> "1 = 0";
            case OTHER -> "UPPER(drawing_format) NOT IN ('X_T', 'STEP', 'STP', 'PDF', 'DWG', 'DXF', 'PNG', 'JPG', 'JPEG', 'TIFF')";
        };
    }

    private AssetType inferType(String format) {
        var normalized = format.toUpperCase(Locale.ROOT);
        if (List.of("X_T", "STEP", "STP").contains(normalized)) {
            return AssetType.THREE_DIMENSIONAL_MODEL;
        }
        if (List.of("PDF", "DWG", "DXF", "PNG", "JPG", "JPEG", "TIFF").contains(normalized)) {
            return AssetType.TWO_DIMENSIONAL_DRAWING;
        }
        return AssetType.OTHER;
    }

    private boolean isPreviewable(String format) {
        return List.of("PDF", "PNG", "JPG", "JPEG", "TIFF")
                .contains(format.toUpperCase(Locale.ROOT));
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
