package com.tianshu.assets.search.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.application.DocumentQueryService;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import com.tianshu.assets.search.application.UnifiedSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class UnifiedSearchControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var assets = new AssetQueryService(new InMemoryAssetRepository());
        var files = new InMemoryFileStorage();
        var documents = new DocumentQueryService(new InMemoryDocumentRepository(files), files);
        mockMvc = standaloneSetup(new UnifiedSearchController(new UnifiedSearchService(assets, documents)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsIndependentAssetAndDocumentSectionsForOneScope() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "")
                        .param("platform_family", "乘用车")
                        .param("platform_variant", "大面水冷")
                        .param("product_line", "H03")
                        .param("base", "宁德基地")
                        .param("production_line", "A 拉线")
                        .param("process_section", "焊接段"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets.status").value("SUCCESS"))
                .andExpect(jsonPath("$.assets.data[0].assetNumber").value("DM-ND-A-0001"))
                .andExpect(jsonPath("$.documents.status").value("SUCCESS"))
                .andExpect(jsonPath("$.documents.data[1].documentNumber").value("DOC-WI-000001"));
    }
}
