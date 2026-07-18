package com.tianshu.assets.asset.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("oceanbase")
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
                SELECT module_tags, standard_equipment_module, linked_module_asset_ids,
                       equipment_interconnect_code
                FROM asset_package_ext WHERE drawing_id = :drawingId
                """).param("drawingId", drawingId).query((rs, row) -> new PackageRow(
                        rs.getString("module_tags"), rs.getBoolean("standard_equipment_module"),
                        rs.getString("linked_module_asset_ids"), rs.getString("equipment_interconnect_code"))).optional();
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
        return Optional.of(new AssetExtensionData(parseStrings(row.moduleTags()), row.standardEquipmentModule(),
                parseLongs(row.linkedModuleAssetIds()), row.equipmentInterconnectCode(), scopes));
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

    private record PackageRow(String moduleTags, boolean standardEquipmentModule,
            String linkedModuleAssetIds, String equipmentInterconnectCode) {}
}
