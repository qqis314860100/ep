package com.tianshu.assets.interconnect.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.interconnect.application.EquipmentInterconnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class EquipmentInterconnectionControllerTest {

    private final MockMvc mockMvc = standaloneSetup(new EquipmentInterconnectionController(new EquipmentInterconnectionService())).build();

    @Test
    void filtersLineDataByEquipmentAndProductionLine() throws Exception {
        mockMvc.perform(get("/api/v1/equipment-interconnections")
                        .param("equipmentCode", "EQ-ND-A-001")
                        .param("production_line", "A 拉线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataReference").value("/line-data/EQ-ND-A-001"));
    }
}
