package com.tianshu.assets.system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.system.application.SystemAdminService;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class SystemAdminControllerTest {

    private MockMvc mockMvc;
    private InMemoryOperationLogStore logs;

    @BeforeEach
    void setUp() {
        logs = new InMemoryOperationLogStore();
        var service = new SystemAdminService(new InMemorySystemUserRepository(), logs);
        mockMvc = standaloneSetup(new SystemAdminController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].userId").value("u-chen"))
                .andExpect(jsonPath("$[0].roles[0]").value("UPLOADER"));
    }

    @Test
    void updatesRolesAndRecordsOperationLog() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"UPLOADER\",\"CONTENT_ADMIN\"],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/v1/admin/operation-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].action").value("ROLE_UPDATE"))
                .andExpect(jsonPath("$.data[0].targetType").value("USER"))
                .andExpect(jsonPath("$.data[0].targetId").value(1));
    }

    @Test
    void rejectsStaleRoleUpdate() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"UPLOADER\"],\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("system_user_conflict"));
    }

    @Test
    void updatesScopes() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/scopes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopes\":[{\"id\":0,\"base\":\"宁德基地\",\"productLine\":\"H03\"}],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes.length()").value(1))
                .andExpect(jsonPath("$.scopes[0].base").value("宁德基地"));
    }

    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/99/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"UPLOADER\"],\"version\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("system_user_not_found"));
    }

    @Test
    void filtersOperationLogsByAction() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"UPLOADER\",\"CONTENT_ADMIN\"],\"version\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/operation-logs").param("action", "ROLE_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));

        mockMvc.perform(get("/api/v1/admin/operation-logs").param("action", "SCOPE_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }
}
