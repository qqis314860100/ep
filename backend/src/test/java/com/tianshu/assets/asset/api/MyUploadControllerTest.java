package com.tianshu.assets.asset.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class MyUploadControllerTest {

    private final InMemoryAssetRepository repository = new InMemoryAssetRepository();
    private final MockMvc mockMvc = standaloneSetup(new MyUploadController(new AssetQueryService(repository))).build();

    @Test
    void listsAssetsUploadedByCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/mine").header("X-User-Name", "陈工"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].ownerName").value("陈工"));
    }

    /** 匿名请求没有「我的」：不能因为 ownerName 为空就退化成「全部资产」。 */
    @Test
    void anonymousRequestSeesNoUploadsInsteadOfEveryAsset() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/uploads/mine").header("X-User-Name", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }
}
