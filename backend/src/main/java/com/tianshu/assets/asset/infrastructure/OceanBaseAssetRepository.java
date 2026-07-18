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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("oceanbase")
public class OceanBaseAssetRepository implements AssetRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, drawing_title, drawing_content, drawing_url, drawing_img,
                   drawing_column, drawing_label, drawing_platform, drawing_line,
                   drawing_format, created_by_name, last_update_date
            FROM sys_drawing
            """;

    private final JdbcClient jdbcClient;
    private final OceanBaseAssetExtensionStore extensionStore;

    public OceanBaseAssetRepository(JdbcClient jdbcClient, OceanBaseAssetExtensionStore extensionStore) {
        this.jdbcClient = jdbcClient;
        this.extensionStore = extensionStore;
    }

    @Override
    public AssetPage search(AssetSearchCriteria criteria) {
        var where = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();

        if (!criteria.query().isBlank()) {
            where.add("(drawing_title LIKE :query OR drawing_content LIKE :query OR drawing_label LIKE :query)");
            parameters.put("query", "%" + criteria.query() + "%");
        }
        if (criteria.assetType() != null) {
            where.add(assetTypePredicate(criteria.assetType()));
        }
        if (criteria.status() != null && criteria.status() != AssetStatus.PENDING_CURATION) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }
        if (criteria.platformFamily() != null && !criteria.platformFamily().isBlank()) {
            where.add("drawing_platform = :platformFamily");
            parameters.put("platformFamily", criteria.platformFamily());
        }
        if (criteria.ownerName() != null && !criteria.ownerName().isBlank()) {
            where.add("created_by_name = :ownerName");
            parameters.put("ownerName", criteria.ownerName());
        }
        if (criteria.platformVariant() != null && !criteria.platformVariant().isBlank()) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }
        if (criteria.productionLine() != null && !criteria.productionLine().isBlank()) {
            where.add("drawing_line = :productionLine");
            parameters.put("productionLine", criteria.productionLine());
        }
        // Legacy rows do not contain a reliable base field, so a base filter cannot match safely.
        if (criteria.base() != null && !criteria.base().isBlank()) {
            return new AssetPage(List.of(), 0, criteria.page(), criteria.perPage());
        }

        var whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        var total = jdbcClient.sql("SELECT COUNT(*) FROM sys_drawing" + whereClause)
                .params(parameters)
                .query(Long.class)
                .single();

        parameters.put("limit", criteria.perPage());
        parameters.put("offset", (criteria.page() - 1) * criteria.perPage());
        var items = jdbcClient.sql(SELECT_COLUMNS + whereClause + " ORDER BY last_update_date DESC, id DESC LIMIT :limit OFFSET :offset")
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
        return List.of();
    }

    @Override
    public Asset save(Asset asset) {
        throw new UnsupportedOperationException("旧系统适配器为只读模式");
    }

    @Override
    public Asset update(Asset asset) {
        throw new UnsupportedOperationException("旧系统适配器为只读模式");
    }

    @Override
    public boolean existsByAssetNumber(String assetNumber) {
        return false;
    }

    private Asset mapAsset(ResultSet resultSet, int rowNumber) throws SQLException {
        var id = resultSet.getLong("id");
        var format = nullable(resultSet.getString("drawing_format"));
        var line = nullable(resultSet.getString("drawing_line"));
        var platform = nullable(resultSet.getString("drawing_platform"));
        var sourceReference = nullable(resultSet.getString("drawing_url"));
        var files = sourceReference.isBlank()
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
        var normalizedScopes = extension == null || extension.scopes().isEmpty()
                ? List.of(new AssetScope(platform, "", "", line, "", platform, ""))
                : extension.scopes();
        return new Asset(
                id,
                "LEGACY-" + String.format(Locale.ROOT, "%012d", id),
                nullable(resultSet.getString("drawing_title")),
                nullable(resultSet.getString("drawing_content")),
                inferType(format),
                AssetStatus.PENDING_CURATION,
                List.of(),
                List.of(),
                extension == null ? List.of() : extension.moduleTags(),
                extension != null && extension.standardEquipmentModule(),
                extension == null ? List.of() : extension.linkedModuleAssetIds(),
                extension == null ? "" : extension.equipmentInterconnectCode(),
                normalizedScopes,
                files,
                nullable(resultSet.getString("created_by_name")),
                "",
                updated == null ? null : updated.toInstant().atOffset(ZoneOffset.UTC).toInstant(),
                true);
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
