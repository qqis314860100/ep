package com.tianshu.assets.document.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.application.DocumentCollaborationService;
import com.tianshu.assets.document.application.DocumentCommandService;
import com.tianshu.assets.document.application.DocumentQueryService;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentCollaborationStore;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class DocumentCollaborationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        var storage = new InMemoryFileStorage();
        var bytes = "%PDF-1.7 collaboration".getBytes(StandardCharsets.UTF_8);
        var storageKey = storage.store(new ByteArrayInputStream(bytes), bytes.length, "collab.pdf", "application/pdf");
        var sha256 = storage.open(storageKey).orElseThrow().sha256();
        var repository = new InMemoryDocumentRepository(storage);
        var commands = new DocumentCommandService(repository, storage);
        var queries = new DocumentQueryService(repository, storage);
        var collab = new DocumentCollaborationService(repository, new InMemoryDocumentCollaborationStore(), storage);
        mockMvc = standaloneSetup(new DocumentController(commands, queries),
                new DocumentCollaborationController(collab))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var draftJson = """
                {
                  "documentNumber":"DOC-COLLAB-1",
                  "title":"协作测试文档",
                  "summary":"验证文档协作。",
                  "categoryCode":"WORK_INSTRUCTION",
                  "maintainerId":"u-100",
                  "maintainerName":"陈工",
                  "maintainerDepartment":"设备工程部",
                  "versionNumber":"V1.0",
                  "changeSummary":"首次发布",
                  "scopeMode":"GLOBAL",
                  "scopes":[],
                  "files":[{"id":0,"name":"collab.pdf","format":"PDF","sizeBytes":25,"previewable":true,
                    "storageKey":"%s","contentSha256":"%s"}]
                }
                """.formatted(storageKey, sha256);
        var draft = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON).content(draftJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        documentId = draft.get("id").asLong();
        versionId = draft.get("currentVersion").get("id").asLong();
        mockMvc.perform(post("/api/v1/documents/{id}/publish", documentId)).andExpect(status().isOk());
    }

    private long documentId;
    private long versionId;

    @Test
    void favoritesCommentsAndLikesRoundTripWithVersionContext() throws Exception {
        mockMvc.perform(post("/api/v1/documents/{id}/favorite", documentId).header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
        mockMvc.perform(get("/api/v1/documents/{id}/favorite", documentId).header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
        mockMvc.perform(get("/api/v1/documents/my/favorites").header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].documentNumber").value("DOC-COLLAB-1"));

        var comment = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/{id}/comments", documentId)
                        .header("X-User-Id", "u-100").header("X-User-Name", "陈工")
                        .contentType(APPLICATION_JSON)
                        .content("{\"versionId\":%d,\"content\":\"请确认新版本要求\"}".formatted(versionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var commentId = comment.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(comment.get("versionId").asLong()).isEqualTo(versionId);

        mockMvc.perform(get("/api/v1/documents/{id}/comments", documentId).header("X-User-Id", "u-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("请确认新版本要求"))
                .andExpect(jsonPath("$[0].versionId").value(versionId));

        mockMvc.perform(post("/api/v1/documents/{id}/comments/{cid}/like", documentId, commentId).header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true));

        mockMvc.perform(delete("/api/v1/documents/{id}/comments/{cid}", documentId, commentId).header("X-User-Id", "u-100"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/{id}/comments", documentId).header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].deleted").value(true));
    }

    @Test
    void listsMineAndRejectsCommentDeletionByOthers() throws Exception {
        mockMvc.perform(get("/api/v1/documents/mine").header("X-User-Id", "u-100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].documentNumber").value("DOC-COLLAB-1"));

        var comment = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/{id}/comments", documentId)
                        .header("X-User-Id", "u-100").header("X-User-Name", "陈工")
                        .contentType(APPLICATION_JSON)
                        .content("{\"versionId\":%d,\"content\":\"待删除评论\"}".formatted(versionId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var commentId = comment.get("id").asLong();

        mockMvc.perform(delete("/api/v1/documents/{id}/comments/{cid}", documentId, commentId).header("X-User-Id", "other-user"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/documents/{id}/comments/{cid}", documentId, commentId)
                        .header("X-User-Id", "admin").header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isNoContent());
    }
}
