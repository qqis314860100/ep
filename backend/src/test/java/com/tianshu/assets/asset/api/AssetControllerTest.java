package com.tianshu.assets.asset.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

class AssetControllerTest {

    private MockMvc mockMvc;
    private InMemoryAssetRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        mockMvc = standaloneSetup(new AssetController(service, writeService), new FavoriteController(service, writeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsPagedSearchResults() throws Exception {
        mockMvc.perform(get("/api/v1/assets").param("q", "焊接"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-ND-A-0001"));
    }

    @Test
    void keepsScopeDimensionsInTheSameScope() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .param("base", "宁德基地")
                        .param("production_line", "B 拉线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void filtersByPlatformFamilyAndVariant() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .param("platform_family", "乘用车")
                        .param("platform_variant", "底部水冷"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void filtersAssetsThatHavePreviewableFiles() throws Exception {
        mockMvc.perform(get("/api/v1/assets").param("previewable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(4))
                .andExpect(jsonPath("$.data[0].id").value(101));
    }

    @Test
    void returnsConsistentNotFoundErrors() throws Exception {
        mockMvc.perform(get("/api/v1/assets/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("asset_not_found"));
    }

    @Test
    void returnsAssetRelations() throws Exception {
        mockMvc.perform(get("/api/v1/assets/101/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relationType").value("REFERENCES"))
                .andExpect(jsonPath("$[1].relationType").value("CONTAINS"));
    }

    @Test
    void savesDraftAndSubmitsItToCuration() throws Exception {
        var request = """
                {
                  "assetNumber":"DM-NEW-0001",
                  "name":"新上传资产",
                  "description":"用于接口测试的资产",
                  "assetType":"MIXED_ASSET",
                  "specialties":["机械"],
                  "scopes":[{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}],
                  "files":[{"id":0,"name":"model.step","format":"STEP","sizeBytes":120,"role":"三维源模型","previewable":false,"primary":true}],
                  "ownerName":"陈工",
                  "ownerDepartment":"设备工程部"
                }
                """;

        mockMvc.perform(post("/api/v1/assets/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(106))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/assets/106/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CURATION"));
    }

    @Test
    void keepsFavoriteStateIdempotentPerUser() throws Exception {
        mockMvc.perform(get("/api/v1/assets/101/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(post("/api/v1/assets/101/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
        mockMvc.perform(post("/api/v1/assets/101/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/assets/101/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void listsFavoritesForCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/assets/101/favorite").header("X-User-Id", "user-a"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/favorites").header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].assetNumber").value("DM-ND-A-0001"));
    }

    @Test
    void supportsCommentDeleteAndIdempotentLike() throws Exception {
        mockMvc.perform(post("/api/v1/assets/101/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"陈工\",\"content\":\"接口评论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("接口评论"))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false))
                .andExpect(jsonPath("$.canDelete").value(true));

        mockMvc.perform(post("/api/v1/assets/101/comments/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(post("/api/v1/assets/101/comments/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(get("/api/v1/assets/101/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].likedByCurrentUser").value(true));
        mockMvc.perform(get("/api/v1/assets/101/comments").header("X-User-Id", "another-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].likedByCurrentUser").value(false))
                .andExpect(jsonPath("$[0].canDelete").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/assets/101/comments/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assets/101/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true));
    }

    @Test
    void acceptsValidatedCommentImages() throws Exception {
        var pngBytes = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x00
        };
        var author = new MockMultipartFile(
                "authorName", "", "text/plain;charset=UTF-8", "陈工".getBytes(StandardCharsets.UTF_8));
        var content = new MockMultipartFile(
                "content", "", "text/plain;charset=UTF-8", "带图反馈".getBytes(StandardCharsets.UTF_8));
        var image = new MockMultipartFile("images", "feedback.png", "image/png", pngBytes);

        mockMvc.perform(multipart("/api/v1/assets/101/comments")
                        .file(author)
                        .file(content)
                        .file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("带图反馈"))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].url").isNotEmpty());
    }

    @Test
    void allowsContentAdministratorsToModerateComments() throws Exception {
        mockMvc.perform(post("/api/v1/assets/101/comments")
                        .header("X-User-Id", "comment-author")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"作者\",\"content\":\"待处理评论\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/101/comments")
                        .header("X-User-Id", "content-admin")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canDelete").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/assets/101/comments/1")
                        .header("X-User-Id", "content-admin")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isOk());
    }
}
