package com.tianshu.assets.interconnect.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.interconnect.application.EquipmentInterconnectionService;
import com.tianshu.assets.interconnect.infrastructure.JdbcEquipmentInterconnectionRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

class EquipmentInterconnectionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:equipment-interconnection;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS asset_equipment_interconnect_ext");
        jdbcTemplate.execute("""
                CREATE TABLE asset_equipment_interconnect_ext (
                    id BIGINT PRIMARY KEY,
                    equipment_code VARCHAR(64),
                    equipment_name VARCHAR(128),
                    base_name VARCHAR(64),
                    production_line VARCHAR(64),
                    process_section VARCHAR(64),
                    interconnect_data_ref VARCHAR(255)
                )""");
        insert(jdbcTemplate, 1L, "EQ-ND-A-001", "焊接工位总成", "宁德基地", "A 拉线", "焊接段",
                "/line-data/EQ-ND-A-001");
        insert(jdbcTemplate, 2L, "EQ-LY-B-012", "PACK 接口设备", "溧阳基地", "B 拉线", "PACK 段",
                "/line-data/EQ-LY-B-012");

        mockMvc = standaloneSetup(new EquipmentInterconnectionController(
                new EquipmentInterconnectionService(
                        new JdbcEquipmentInterconnectionRepository(JdbcClient.create(dataSource))))).build();
    }

    @Test
    void filtersLineDataByEquipmentAndProductionLine() throws Exception {
        mockMvc.perform(get("/api/v1/equipment-interconnections")
                        .param("equipmentCode", "EQ-ND-A-001")
                        .param("production_line", "A 拉线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataReference").value("/line-data/EQ-ND-A-001"));
    }

    @Test
    void returnsEveryRecordWhenNoFilterIsGiven() throws Exception {
        mockMvc.perform(get("/api/v1/equipment-interconnections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private void insert(
            JdbcTemplate jdbcTemplate, long id, String code, String name, String base,
            String productionLine, String section, String dataReference) {
        jdbcTemplate.update("""
                INSERT INTO asset_equipment_interconnect_ext
                (id, equipment_code, equipment_name, base_name, production_line, process_section,
                 interconnect_data_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                id, code, name, base, productionLine, section, dataReference);
    }
}
