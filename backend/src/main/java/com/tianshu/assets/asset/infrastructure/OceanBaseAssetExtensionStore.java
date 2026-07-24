package com.tianshu.assets.asset.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile({"oceanbase", "local"})
public class OceanBaseAssetExtensionStore {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public OceanBaseAssetExtensionStore(JdbcClient jdbcClient, ObjectMapper objectMapper,
            @Value("${asset.extension-schema-enabled:false}") boolean enabled) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public Optional<AssetExtensionData> find(long drawingId) {
        if (!enabled) return Optional.empty();
        var packageData = jdbcClient.sql("""
                SELECT asset_number, asset_type, status, module_tags, standard_equipment_module,
                       linked_module_asset_ids, equipment_interconnect_code, owner_department
                FROM asset_package_ext WHERE drawing_id = :drawingId
                """).param("drawingId", drawingId).query((rs, row) -> new PackageRow(
                        rs.getString("asset_number"), rs.getString("asset_type"), rs.getString("status"),
                        rs.getString("module_tags"), rs.getBoolean("standard_equipment_module"),
                        rs.getString("linked_module_asset_ids"), rs.getString("equipment_interconnect_code"),
                        rs.getString("owner_department"))).optional();
        if (packageData.isEmpty()) return Optional.empty();
        var scopes = jdbcClient.sql("""
                SELECT platform_family, platform_variant, product_line, base_name,
                       production_line, process_section
                FROM asset_scope_ext WHERE drawing_id = :drawingId ORDER BY id
                """).param("drawingId", drawingId).query((rs, row) -> new AssetScope(
                        rs.getString("platform_family"), rs.getString("product_line"), rs.getString("base_name"),
                        rs.getString("production_line"), rs.getString("process_section"),
                        rs.getString("platform_family"), rs.getString("platform_variant"))).list();
        var row = packageData.get();
        var files = jdbcClient.sql("""
                SELECT id, original_name, format, size_bytes, role, previewable, is_primary,
                       storage_key, content_sha256
                FROM asset_file_ext
                WHERE drawing_id = :drawingId AND file_status = 'AVAILABLE'
                ORDER BY is_primary DESC, id
                """).param("drawingId", drawingId).query((rs, ignored) -> new AssetFile(
                        rs.getLong("id"), rs.getString("original_name"), rs.getString("format"),
                        rs.getLong("size_bytes"), rs.getString("role"), rs.getBoolean("previewable"),
                        rs.getBoolean("is_primary"), rs.getString("storage_key"),
                        rs.getString("content_sha256"))).list();
        return Optional.of(new AssetExtensionData(
                nullable(row.assetNumber()), enumValue(AssetType.class, row.assetType(), AssetType.OTHER),
                enumValue(AssetStatus.class, row.status(), AssetStatus.PENDING_CURATION),
                parseStrings(row.moduleTags()), row.standardEquipmentModule(),
                parseLongs(row.linkedModuleAssetIds()), nullable(row.equipmentInterconnectCode()),
                nullable(row.ownerDepartment()), scopes, files));
    }

    boolean enabled() {
        return enabled;
    }

    private List<String> parseStrings(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Long> parseLongs(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private record PackageRow(String assetNumber, String assetType, String status, String moduleTags,
            boolean standardEquipmentModule, String linkedModuleAssetIds, String equipmentInterconnectCode,
            String ownerDepartment) {}
}
