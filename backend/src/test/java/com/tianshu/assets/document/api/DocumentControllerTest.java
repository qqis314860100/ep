package com.tianshu.assets.document.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.common.preview.DocumentPreviewConverter;
import com.tianshu.assets.document.application.DocumentCommandService;
import com.tianshu.assets.document.application.DocumentQueryService;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class DocumentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private String storageKey;
    private String sha256;

    @BeforeEach
    void setUp() throws Exception {
        var storage = new InMemoryFileStorage();
        var bytes = "%PDF-1.7 controller".getBytes(StandardCharsets.UTF_8);
        storageKey = storage.store(new ByteArrayInputStream(bytes), bytes.length, "controller.pdf", "application/pdf");
        sha256 = storage.open(storageKey).orElseThrow().sha256();
        var repository = new InMemoryDocumentRepository(storage);
        var commands = new DocumentCommandService(repository, storage);
        var queries = new DocumentQueryService(repository, storage);
        mockMvc = standaloneSetup(new DocumentController(commands, queries))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsPublishesSearchesAndReadsTheCurrentDocument() throws Exception {
        var draftBody = mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(validDraftJson("")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.documentNumber").value(org.hamcrest.Matchers.matchesPattern("DOC-\\d{6}")))
                .andReturn().getResponse().getContentAsString();
        var id = objectMapper.readTree(draftBody).get("id").asLong();

        mockMvc.perform(post("/api/v1/documents/{id}/publish", id)
                        .header("X-User-Id", "u-100")
                        .header("X-User-Name", "陈工"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentVersion.versionNumber").value("V1.0"));

        mockMvc.perform(get("/api/v1/documents").param("q", "接口测试文档"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id));

        mockMvc.perform(get("/api/v1/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion.files[0].name").value("controller.pdf"));
    }

    @Test
    void streamsCurrentVersionFilesThroughGuardedDocumentRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/documents/101/versions/1001/files/2001").param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));

        mockMvc.perform(get("/api/v1/documents/101/versions/1001/files/2001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void convertsDocxToPdfForInlinePreviewAndDegradesWhenConverterUnavailable() throws Exception {
        var storage = new InMemoryFileStorage();
        var docxBytes = "docx-bytes".getBytes(StandardCharsets.UTF_8);
        var docxKey = storage.store(new ByteArrayInputStream(docxBytes), docxBytes.length, "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        var docxSha = storage.open(docxKey).orElseThrow().sha256();
        var repository = new InMemoryDocumentRepository(storage);
        var commands = new DocumentCommandService(repository, storage);
        var queries = new DocumentQueryService(repository, storage);
        var converter = new DocumentPreviewConverter() {
            @Override
            public boolean supports(String format) {
                return "DOCX".equals(format);
            }

            @Override
            public Optional<byte[]> toPdf(String format, byte[] source) {
                return Optional.of("%PDF-doc".getBytes(StandardCharsets.UTF_8));
            }
        };
        var mvc = standaloneSetup(new DocumentController(commands, queries, converter))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        var draft = objectMapper.readTree(mvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(docxDraftJson("DOC-CONV-0001", docxKey, docxSha)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var id = draft.get("id").asLong();
        var versionId = draft.get("currentVersion").get("id").asLong();
        var fileId = draft.get("currentVersion").get("files").get(0).get("id").asLong();
        mvc.perform(post("/api/v1/documents/{id}/publish", id))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}/files/{fileId}", id, versionId, fileId)
                        .param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().bytes("%PDF-doc".getBytes(StandardCharsets.UTF_8)));

        var unavailableMvc = standaloneSetup(new DocumentController(commands, queries,
                new DocumentPreviewConverter() {
                    @Override
                    public boolean supports(String format) {
                        return "DOCX".equals(format);
                    }

                    @Override
                    public Optional<byte[]> toPdf(String format, byte[] source) {
                        return Optional.empty();
                    }
                }))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        unavailableMvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}/files/{fileId}", id, versionId, fileId)
                        .param("preview", "true"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsStableValidationAndConflictErrors() throws Exception {
        mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(validDraftJson("DOC-WI-000001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("duplicate_document_number"));

        mockMvc.perform(get("/api/v1/documents/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("document_not_found"));
    }

    @Test
    void returnsPublishValidationAndRepeatedPublishConflicts() throws Exception {
        var missingDraft = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(validDraftJson("DOC-MISSING-1").replace(storageKey, "missing-key")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/documents/{id}/publish", missingDraft))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("document_publish_invalid"));

        var draft = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(validDraftJson("DOC-REPEAT-1")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/documents/{id}/publish", draft)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/documents/{id}/publish", draft))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("document_state_conflict"));
    }

    private String validDraftJson(String documentNumber) {
        return """
                {
                  "documentNumber":"%s",
                  "title":"接口测试文档",
                  "summary":"用于验证文档中心接口闭环。",
                  "categoryCode":"WORK_INSTRUCTION",
                  "maintainerId":"u-100",
                  "maintainerName":"陈工",
                  "maintainerDepartment":"设备工程部",
                  "versionNumber":"V1.0",
                  "changeSummary":"首次发布",
                  "scopeMode":"GLOBAL",
                  "scopes":[],
                  "files":[{
                    "id":0,
                    "name":"controller.pdf",
                    "format":"PDF",
                    "sizeBytes":19,
                    "previewable":true,
                    "storageKey":"%s",
                    "contentSha256":"%s"
                  }]
                }
                """.formatted(documentNumber, storageKey, sha256);
    }

    private String docxDraftJson(String documentNumber, String key, String digest) {
        return """
                {
                  "documentNumber":"%s",
                  "title":"DOCX 转换接口测试",
                  "summary":"用于验证 DOCX 在线预览转换。",
                  "categoryCode":"WORK_INSTRUCTION",
                  "maintainerId":"u-100",
                  "maintainerName":"陈工",
                  "maintainerDepartment":"设备工程部",
                  "versionNumber":"V1.0",
                  "changeSummary":"首次发布",
                  "scopeMode":"GLOBAL",
                  "scopes":[],
                  "files":[{
                    "id":0,
                    "name":"notes.docx",
                    "format":"DOCX",
                    "sizeBytes":10,
                    "previewable":true,
                    "storageKey":"%s",
                    "contentSha256":"%s"
                  }]
                }
                """.formatted(documentNumber, key, digest);
    }
}
