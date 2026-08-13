package com.tianshu.assets.common.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class LibreOfficeDocumentPreviewConverterTest {

    @Test
    void declaresOnlyOfficeDocumentFormats() {
        var converter = new LibreOfficeDocumentPreviewConverter("definitely-missing-soffice");
        assertThat(converter.supports("DOCX")).isTrue();
        assertThat(converter.supports("DOC")).isTrue();
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
        var converter = new LibreOfficeDocumentPreviewConverter(script.toString());

        var pdf = converter.toPdf("DOCX", "dummy-docx".getBytes(StandardCharsets.UTF_8));

        assertThat(pdf).isPresent();
        assertThat(new String(pdf.orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("%PDF-fake");
    }
}
