package com.tianshu.assets.common.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class LibreOfficeDocumentPreviewConverterTest {

    @Test
    void declaresSupportedOfficeAndTextFormats() {
        var converter = new LibreOfficeDocumentPreviewConverter("definitely-missing-soffice");
        for (var format : java.util.List.of("DOCX", "DOC", "XLSX", "XLS", "PPTX", "PPT", "CSV", "TXT")) {
            assertThat(converter.supports(format)).as(format).isTrue();
        }
        assertThat(converter.supports("PDF")).isFalse();
        assertThat(converter.supports("X_T")).isFalse();
        assertThat(converter.supports(null)).isFalse();
    }

    @Test
    void returnsEmptyWhenBinaryIsUnavailable() {
        var converter = new LibreOfficeDocumentPreviewConverter("/nonexistent/soffice");
        assertThat(converter.toPdf("DOCX", "dummy".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void returnsEmptyForUnsupportedFormatOrEmptySource() {
        var converter = new LibreOfficeDocumentPreviewConverter("/nonexistent/soffice");
        assertThat(converter.toPdf("PDF", "dummy".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(converter.toPdf("DOCX", new byte[0])).isEmpty();
    }

    @Test
    void convertsThroughHeadlessBinaryAndReturnsPdf() throws Exception {
        var converter = fakeConverter();
        var pdf = converter.toPdf("DOCX", "dummy-docx".getBytes(StandardCharsets.UTF_8));
        assertThat(pdf).isPresent();
        assertThat(new String(pdf.orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("%PDF-fake");
    }

    @Test
    void convertsEachSupportedFormatWithOwnInputExtension() throws Exception {
        var converter = fakeConverter();
        for (var format : java.util.List.of("DOCX", "XLSX", "XLS", "PPTX", "PPT", "CSV", "TXT")) {
            var pdf = converter.toPdf(format, ("dummy-" + format).getBytes(StandardCharsets.UTF_8));
            assertThat(pdf).as(format).isPresent();
        }
    }

    private LibreOfficeDocumentPreviewConverter fakeConverter() throws Exception {
        var dir = Files.createTempDirectory("fake-soffice-");
        var script = dir.resolve("soffice");
        Files.writeString(script, """
                #!/bin/sh
                outdir=''
                for arg in "$@"; do
                  [ "$prev" = "--outdir" ] && outdir="$arg"
                  prev="$arg"
                done
                printf '%%PDF-fake' > "$outdir/source.pdf"
                exit 0
                """);
        script.toFile().setExecutable(true);
        return new LibreOfficeDocumentPreviewConverter(script.toString());
    }
}
