package com.tianshu.assets.asset.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class AssetFileUploadControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new AssetFileUploadController(new InMemoryFileStorage()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void storesValidPdfAndReturnsIntegrityMetadata() throws Exception {
        var file = new MockMultipartFile("file", "layout.pdf", "application/pdf", "%PDF-1.7 demo".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.name").value("layout.pdf"))
                .andExpect(jsonPath("$.file.format").value("PDF"))
                .andExpect(jsonPath("$.file.storageKey").isNotEmpty())
                .andExpect(jsonPath("$.file.contentSha256").isNotEmpty());
    }

    @Test
    void rejectsExtensionAndContentMismatch() throws Exception {
        var file = new MockMultipartFile("file", "layout.pdf", "application/pdf", "not a pdf".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("file_invalid"));
    }

    @Test
    void rejectsExecutableFiles() throws Exception {
        var file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "MZ".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("file_invalid"));
    }

    @Test
    void marksDocxAsPreviewableWithGenericRole() throws Exception {
        var file = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] { 0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4 });
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.format").value("DOCX"))
                .andExpect(jsonPath("$.file.previewable").value(true))
                .andExpect(jsonPath("$.file.role").value("其他附件"));
    }

    @Test
    void rejectsDocxWithMismatchedSignature() throws Exception {
        var file = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a zip archive".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("file_invalid"));
    }
}
