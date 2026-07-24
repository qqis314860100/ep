package com.tianshu.assets.interconnect.application;

import com.tianshu.assets.interconnect.domain.EquipmentInterconnection;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class EquipmentInterconnectionService {

    private final JdbcClient jdbcClient;

    private final List<EquipmentInterconnection> links = List.of(
            new EquipmentInterconnection(1, "EQ-ND-A-001", "焊接工位总成", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-001", "ACTIVE"),
            new EquipmentInterconnection(2, "EQ-ND-A-002", "定位工装设备", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-002", "ACTIVE"),
            new EquipmentInterconnection(3, "EQ-ND-A-003", "输送模块设备", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-003", "ACTIVE"),
            new EquipmentInterconnection(4, "EQ-LY-B-012", "PACK 接口设备", "溧阳基地", "B 拉线", "PACK 段", "/line-data/EQ-LY-B-012", "ACTIVE"));

    public EquipmentInterconnectionService() {
        this.jdbcClient = null;
    }

    @Autowired
    public EquipmentInterconnectionService(org.springframework.beans.factory.ObjectProvider<JdbcClient> jdbcClientProvider) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
    }

    public List<EquipmentInterconnection> search(String equipmentCode, String base, String productionLine) {
        if (jdbcClient != null) {
            var where = new java.util.ArrayList<String>();
            var params = new java.util.LinkedHashMap<String, Object>();
            if (hasText(equipmentCode)) {
                where.add("equipment_code LIKE :equipmentCode");
                params.put("equipmentCode", "%" + equipmentCode + "%");
            }
            if (hasText(base)) {
                where.add("base_name LIKE :base");
                params.put("base", "%" + base + "%");
            }
            if (hasText(productionLine)) {
                where.add("production_line LIKE :productionLine");
                params.put("productionLine", "%" + productionLine + "%");
            }
            var whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
            return jdbcClient.sql("""
                    SELECT id, equipment_code, equipment_name, base_name, production_line, process_section,
                           interconnect_data_ref
                    FROM asset_equipment_interconnect_ext
                    """ + whereClause + " ORDER BY id")
                    .params(params)
                    .query((rs, ignored) -> new EquipmentInterconnection(
                            rs.getLong("id"), nullable(rs.getString("equipment_code")),
                            nullable(rs.getString("equipment_name")), nullable(rs.getString("base_name")),
                            nullable(rs.getString("production_line")), nullable(rs.getString("process_section")),
                            nullable(rs.getString("interconnect_data_ref")), "ACTIVE"))
                    .list();
        }
        return links.stream().filter(link -> matches(link.equipmentCode(), equipmentCode)
                && matches(link.base(), base)
                && matches(link.productionLine(), productionLine)).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank()
                || actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
