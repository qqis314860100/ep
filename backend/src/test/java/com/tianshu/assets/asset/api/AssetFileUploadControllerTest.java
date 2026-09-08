package com.tianshu.assets.asset.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
                zipWith(Map.of("word/document.xml", "<w:document/>")));
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

    @Test
    void marksXlsxAndPptxAsPreviewableWhenMarkerEntryPresent() throws Exception {
        var xlsx = new MockMultipartFile("file", "sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                zipWith(Map.of("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>")));
        mockMvc.perform(multipart("/api/v1/uploads/files").file(xlsx))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.format").value("XLSX"))
                .andExpect(jsonPath("$.file.previewable").value(true));

        var pptx = new MockMultipartFile("file", "deck.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                zipWith(Map.of("ppt/presentation.xml", "<presentation/>")));
        mockMvc.perform(multipart("/api/v1/uploads/files").file(pptx))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.format").value("PPTX"))
                .andExpect(jsonPath("$.file.previewable").value(true));
    }

    @Test
    void rejectsZipWithoutExpectedOoxmlMarker() throws Exception {
        var renamed = new MockMultipartFile("file", "fake.xlsx", "application/octet-stream",
                zipWith(Map.of("some.txt", "hello")));
        mockMvc.perform(multipart("/api/v1/uploads/files").file(renamed))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("file_invalid"));
    }

    @Test
    void acceptsLegacyOfficeWithOleHeaderAndMarksPreviewable() throws Exception {
        for (var extension : new String[] { "doc", "xls", "ppt" }) {
            var oleBytes = oleHeaderWithPadding();
            var file = new MockMultipartFile("file", "legacy." + extension, "application/octet-stream", oleBytes);
            mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.file.format").value(extension.toUpperCase()))
                    .andExpect(jsonPath("$.file.previewable").value(true));
        }
    }

    @Test
    void rejectsLegacyOfficeWithoutOleHeader() throws Exception {
        var file = new MockMultipartFile("file", "fake.xls", "application/octet-stream", "plain text".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("file_invalid"));
    }

    @Test
    void acceptsCsvAndTxtAndMarksPreviewable() throws Exception {
        var csv = new MockMultipartFile("file", "data.csv", "text/csv", "a,b\n1,2\n".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(csv))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.format").value("CSV"))
                .andExpect(jsonPath("$.file.previewable").value(true))
                .andExpect(jsonPath("$.file.role").value("其他附件"));
        var txt = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/files").file(txt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.format").value("TXT"))
                .andExpect(jsonPath("$.file.previewable").value(true));
    }

    private static byte[] zipWith(Map<String, String> entries) throws Exception {
        var buffer = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static byte[] oleHeaderWithPadding() {
        var bytes = new byte[64];
        bytes[0] = (byte) 0xD0;
        bytes[1] = (byte) 0xCF;
        bytes[2] = 0x11;
        bytes[3] = (byte) 0xE0;
        bytes[4] = (byte) 0xA1;
        bytes[5] = (byte) 0xB1;
        bytes[6] = 0x1A;
        bytes[7] = (byte) 0xE1;
        return bytes;
    }
}
