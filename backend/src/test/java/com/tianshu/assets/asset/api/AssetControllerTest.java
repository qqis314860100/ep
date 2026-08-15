package com.tianshu.assets.asset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.application.AssetRelationService;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.common.preview.DocumentPreviewConverter;
import com.tianshu.assets.common.preview.NoopDocumentPreviewConverter;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class AssetControllerTest {

    private MockMvc mockMvc;
    private InMemoryAssetRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        mockMvc = standaloneSetup(new AssetController(service, writeService),
                new FavoriteController(service, writeService),
                new AssetRelationController(new AssetRelationService(repository, new InMemoryOperationLogStore())))
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
    void createsRelationVisibleFromBothSides() throws Exception {
        mockMvc.perform(post("/api/v1/assets/102/relations")
                        .header("X-User-Id", "emp-chen")
                        .header("X-User-Name", "陈工")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":104,\"relationType\":\"REFERENCES\",\"description\":\"定位工装引用历史设备图\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationType").value("REFERENCES"))
                .andExpect(jsonPath("$.directionLabel").value("引用"))
                .andExpect(jsonPath("$.targetAssetNumber").value("LEGACY-00000104"));

        mockMvc.perform(get("/api/v1/assets/102/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relationType=='REFERENCES' && @.targetAssetId==104)].directionLabel").value(org.hamcrest.Matchers.contains("引用")));
        mockMvc.perform(get("/api/v1/assets/104/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relationType=='REFERENCES' && @.sourceAssetId==102)].directionLabel").value(org.hamcrest.Matchers.contains("被引用")));
    }

    @Test
    void rejectsSelfDuplicateAndContainsCycleRelations() throws Exception {
        mockMvc.perform(post("/api/v1/assets/102/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":102,\"relationType\":\"CONTAINS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("asset_relation_conflict"));

        mockMvc.perform(post("/api/v1/assets/101/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":102,\"relationType\":\"REFERENCES\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("asset_relation_conflict"));

        mockMvc.perform(post("/api/v1/assets/103/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":101,\"relationType\":\"CONTAINS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("asset_relation_conflict"));

        mockMvc.perform(post("/api/v1/assets/103/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":102,\"relationType\":\"CONTAINS\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void disablesAssetWithReasonAndHidesItFromDefaultSearch() throws Exception {
        mockMvc.perform(post("/api/v1/assets/103/disable")
                        .header("X-User-Roles", "UPLOADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"该产线已停产\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("operation_forbidden"));

        mockMvc.perform(post("/api/v1/assets/103/disable")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        mockMvc.perform(post("/api/v1/assets/103/disable")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"该产线已停产\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/assets").param("q", "输送模块"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));

        mockMvc.perform(get("/api/v1/assets").param("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-ND-A-0003"));

        mockMvc.perform(get("/api/v1/assets/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(103));
    }

    @Test
    void updatesAndRemovesRelationWithAudit() throws Exception {
        var createdJson = mockMvc.perform(post("/api/v1/assets/102/relations")
                        .header("X-User-Id", "emp-chen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAssetId\":104,\"relationType\":\"MATCHES\",\"description\":\"配套\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var created = objectMapper.readTree(createdJson);
        var relationId = created.get("id").asLong();

        var patchResult = mockMvc.perform(patch("/api/v1/assets/102/relations/{id}", relationId)
                        .header("X-User-Id", "emp-chen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAssetId\":102,\"targetAssetId\":104,\"relationType\":\"REPLACES\",\"description\":\"替代说明\",\"version\":0}"))
                .andReturn().getResponse();
        if (patchResult.getStatus() != 200) {
            throw new AssertionError("PATCH failed: " + patchResult.getStatus() + " " + patchResult.getContentAsString());
        }

        mockMvc.perform(patch("/api/v1/assets/102/relations/{id}", relationId)
                        .header("X-User-Id", "emp-chen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAssetId\":102,\"targetAssetId\":104,\"relationType\":\"MATCHES\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("asset_relation_version_conflict"));

        mockMvc.perform(delete("/api/v1/assets/102/relations/{id}", relationId)
                        .header("X-User-Id", "emp-chen"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/assets/102/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)]", relationId).isEmpty());
        mockMvc.perform(get("/api/v1/assets/104"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(104));
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
    void downloadsAssetFilesAsZipPackage() throws Exception {
        var storage = new InMemoryFileStorage();
        var key = storage.store(new ByteArrayInputStream("model-bytes".getBytes(StandardCharsets.UTF_8)),
                11, "model.step", "application/octet-stream");
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        var mvc = standaloneSetup(new AssetController(service, writeService, storage),
                new FavoriteController(service, writeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var request = """
                {
                  "assetNumber":"DM-ZIP-0001",
                  "name":"打包测试资产",
                  "description":"用于打包下载测试",
                  "assetType":"THREE_DIMENSIONAL_MODEL",
                  "specialties":["机械"],
                  "scopes":[{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}],
                  "files":[{"id":0,"name":"model.step","format":"STEP","sizeBytes":11,"role":"三维源模型","previewable":false,"primary":true,"storageKey":"%s"}],
                  "ownerName":"陈工",
                  "ownerDepartment":"设备工程部"
                }
                """.formatted(key);

        mvc.perform(post("/api/v1/assets/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(106));

        var result = mvc.perform(get("/api/v1/assets/106/package"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains(".zip");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            var entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("model.step");
        }
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

    @Test
    void convertsPreviewableDocxToPdfForInlinePreview() throws Exception {
        var storage = new InMemoryFileStorage();
        var key = storage.store(new ByteArrayInputStream("docx-bytes".getBytes(StandardCharsets.UTF_8)), 10,
                "notes.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        var converter = new DocumentPreviewConverter() {
            @Override
            public boolean supports(String format) {
                return "DOCX".equals(format);
            }

            @Override
            public Optional<byte[]> toPdf(String format, byte[] source) {
                return Optional.of("%PDF-converted".getBytes(StandardCharsets.UTF_8));
            }
        };
        var mvc = standaloneSetup(new AssetController(service, writeService, storage, converter),
                new FavoriteController(service, writeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var request = """
                {
                  "assetNumber":"DM-DOCX-0001",
                  "name":"DOCX 预览资产",
                  "description":"用于 DOCX 在线预览转换测试",
                  "assetType":"THREE_DIMENSIONAL_MODEL",
                  "specialties":["机械"],
                  "scopes":[{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}],
                  "files":[{"id":0,"name":"notes.docx","format":"DOCX","sizeBytes":10,"role":"其他附件","previewable":true,"primary":true,"storageKey":"%s"}],
                  "ownerName":"陈工",
                  "ownerDepartment":"设备工程部"
                }
                """.formatted(key);
        var created = mvc.perform(post("/api/v1/assets/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var json = new ObjectMapper().readTree(created);
        var id = json.get("id").asLong();
        var fileId = json.get("files").get(0).get("id").asLong();

        mvc.perform(get("/api/v1/assets/{id}/files/{fileId}", id, fileId).param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().bytes("%PDF-converted".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void returnsServiceUnavailableWhenDocxConversionIsUnavailable() throws Exception {
        var storage = new InMemoryFileStorage();
        var key = storage.store(new ByteArrayInputStream("docx-bytes".getBytes(StandardCharsets.UTF_8)), 10,
                "notes.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        var converter = new DocumentPreviewConverter() {
            @Override
            public boolean supports(String format) {
                return "DOCX".equals(format);
            }

            @Override
            public Optional<byte[]> toPdf(String format, byte[] source) {
                return Optional.empty();
            }
        };
        var mvc = standaloneSetup(new AssetController(service, writeService, storage, converter),
                new FavoriteController(service, writeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var request = """
                {
                  "assetNumber":"DM-DOCX-0002",
                  "name":"DOCX 降级资产",
                  "description":"用于 DOCX 预览降级测试",
                  "assetType":"THREE_DIMENSIONAL_MODEL",
                  "specialties":["机械"],
                  "scopes":[{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}],
                  "files":[{"id":0,"name":"notes.docx","format":"DOCX","sizeBytes":10,"role":"其他附件","previewable":true,"primary":true,"storageKey":"%s"}],
                  "ownerName":"陈工",
                  "ownerDepartment":"设备工程部"
                }
                """.formatted(key);
        var created = mvc.perform(post("/api/v1/assets/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var json = new ObjectMapper().readTree(created);
        var id = json.get("id").asLong();
        var fileId = json.get("files").get(0).get("id").asLong();

        mvc.perform(get("/api/v1/assets/{id}/files/{fileId}", id, fileId).param("preview", "true"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void filtersAssetsBySpecialtyFormatOwnerScopeAndMissingScope() throws Exception {
        mockMvc.perform(get("/api/v1/assets").param("specialty", "电气"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-LY-B-0012"));

        mockMvc.perform(get("/api/v1/assets").param("format", "X_T"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-ND-A-0001"));

        mockMvc.perform(get("/api/v1/assets").param("owner", "陈工"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-ND-A-0001"));

        mockMvc.perform(get("/api/v1/assets")
                        .param("product_line", "H03")
                        .param("process_section", "焊接段"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(3));

        mockMvc.perform(get("/api/v1/assets").param("missing_scope", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data[0].assetNumber").value("LEGACY-00000104"));
    }

    @Test
    void filtersAssetsByUpdatedTimeRangeAndSortsResults() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .param("updated_from", "2026-07-13T20:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-ND-A-0001"));

        mockMvc.perform(get("/api/v1/assets").param("sort", "ASSET_NUMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-LY-B-0012"))
                .andExpect(jsonPath("$.data[4].assetNumber").value("LEGACY-00000104"));

        mockMvc.perform(get("/api/v1/assets").param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetNumber").value("DM-LY-B-0012"));
    }

    @Test
    void rejectsPackageDownloadWhenTotalSizeExceedsConfiguredCap() throws Exception {
        var storage = new InMemoryFileStorage();
        var key = storage.store(new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)), 10,
                "model.step", "application/octet-stream");
        var service = new AssetQueryService(repository);
        var writeService = new AssetWriteService(repository);
        var mvc = standaloneSetup(new AssetController(service, writeService, storage,
                new NoopDocumentPreviewConverter(), 9), new FavoriteController(service, writeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var request = """
                {
                  "assetNumber":"DM-ZIP-CAP-1",
                  "name":"打包超限资产",
                  "description":"用于打包大小上限测试",
                  "assetType":"THREE_DIMENSIONAL_MODEL",
                  "specialties":["机械"],
                  "scopes":[{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段"}],
                  "files":[{"id":0,"name":"model.step","format":"STEP","sizeBytes":10,"role":"三维源模型","previewable":false,"primary":true,"storageKey":"%s"}],
                  "ownerName":"陈工",
                  "ownerDepartment":"设备工程部"
                }
                """.formatted(key);
        var created = mvc.perform(post("/api/v1/assets/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var id = new ObjectMapper().readTree(created).get("id").asLong();

        mvc.perform(get("/api/v1/assets/{id}/package", id))
                .andExpect(status().isPayloadTooLarge());
    }
}
