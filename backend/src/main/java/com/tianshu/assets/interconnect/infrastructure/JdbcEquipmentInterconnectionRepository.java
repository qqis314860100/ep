package com.tianshu.assets.interconnect.infrastructure;

import com.tianshu.assets.interconnect.domain.EquipmentInterconnection;
import com.tianshu.assets.interconnect.domain.EquipmentInterconnectionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEquipmentInterconnectionRepository implements EquipmentInterconnectionRepository {

    private final JdbcClient jdbcClient;

    public JdbcEquipmentInterconnectionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<EquipmentInterconnection> search(String equipmentCode, String base, String productionLine) {
        var where = new ArrayList<String>();
        var params = new LinkedHashMap<String, Object>();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
