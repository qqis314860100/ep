package com.tianshu.assets.dictionary.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.dictionary.application.DictionaryService;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class DictionaryControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var service = new DictionaryService(new InMemoryDictionaryStore());
        mockMvc = standaloneSetup(new DictionaryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsCategoriesAndPlatformHierarchy() throws Exception {
        mockMvc.perform(get("/api/v1/dictionaries/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PLATFORM_FAMILY"))
                .andExpect(jsonPath("$[2].code").value("PRODUCT_LINE"))
                .andExpect(jsonPath("$[2].name").value("蓝本"));

        mockMvc.perform(get("/api/v1/dictionaries/items")
                        .param("category", "PLATFORM_VARIANT")
                        .param("parent_id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("大面水冷"));
    }

    @Test
    void exposesDocumentCategories() throws Exception {
        mockMvc.perform(get("/api/v1/dictionaries/items").param("category", "DOCUMENT_CATEGORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("技术规范"))
                .andExpect(jsonPath("$[5].name").value("标准模板"));
    }

    @Test
    void createsAndUpdatesDictionaryItem() throws Exception {
        var response = mockMvc.perform(post("/api/v1/dictionaries/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SPECIALTY","code":"VISION","name":"视觉", "status":"ENABLED",
                                 "sortOrder":50,"directional":false,"allowDuplicate":false,"version":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("视觉"))
                .andReturn().getResponse().getContentAsString();

        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        mockMvc.perform(patch("/api/v1/dictionaries/items/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SPECIALTY","code":"VISION","name":"机器视觉", "status":"ENABLED",
                                 "sortOrder":50,"directional":false,"allowDuplicate":false,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("机器视觉"));
    }

    @Test
    void rejectsStaleVersionAndInvalidParent() throws Exception {
        mockMvc.perform(patch("/api/v1/dictionaries/items/201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SPECIALTY","code":"MECHANICAL","name":"机械工程", "status":"ENABLED",
                                 "sortOrder":10,"directional":false,"allowDuplicate":false,"version":8}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("dictionary_item_conflict"));

        mockMvc.perform(post("/api/v1/dictionaries/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"PRODUCTION_LINE","code":"LINE_X","name":"X 拉线", "status":"ENABLED",
                                 "sortOrder":10,"directional":false,"allowDuplicate":false,"version":0}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mergesUsedItemWithoutDeletingHistory() throws Exception {
        mockMvc.perform(post("/api/v1/dictionaries/items/212/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":213,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MERGED"))
                .andExpect(jsonPath("$.mergeTargetId").value(213));
    }
}
