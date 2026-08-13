package com.tianshu.assets.common.preview;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 LibreOffice headless 的办公文档转 PDF 转换器。
 *
 * <p>部署环境需安装 LibreOffice（macOS 可用 Homebrew：{@code brew install --cask libreoffice}），
 * 或通过 {@code asset.libreoffice-binary} 指定 soffice 可执行文件路径。转换器缺失或转换失败时
 * 返回 {@link Optional#empty()}，由上层降级为「暂不支持在线预览」，不会抛出异常。</p>
 */
@Component
public class LibreOfficeDocumentPreviewConverter implements DocumentPreviewConverter {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("DOCX", "DOC");
    private static final long CONVERT_TIMEOUT_SECONDS = 30;

    private final String binary;
    private volatile Boolean available;

    public LibreOfficeDocumentPreviewConverter(
            @Value("${asset.libreoffice-binary:soffice}") String binary) {
        this.binary = binary;
    }

    @Override
    public boolean supports(String format) {
        return format != null && SUPPORTED_FORMATS.contains(format.toUpperCase(Locale.ROOT));
    }

    @Override
    public Optional<byte[]> toPdf(String format, byte[] source) {
        if (!supports(format) || source == null || source.length == 0 || !available()) {
            return Optional.empty();
        }
        var extension = "DOC".equalsIgnoreCase(format) ? ".doc" : ".docx";
        try {
            var workDir = Files.createTempDirectory("docx-preview-");
            try {
                var input = workDir.resolve("source" + extension);
                Files.write(input, source);
                var profile = workDir.resolve("lo-profile");
                var process = new ProcessBuilder(
                        resolvedBinary(),
                        "--headless",
                        "-env:UserInstallation=file://" + profile,
                        "--convert-to", "pdf",
                        "--outdir", workDir.toString(),
                        input.toString())
                        .redirectErrorStream(true)
                        .start();
                if (!process.waitFor(CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return Optional.empty();
                }
                if (process.exitValue() != 0) return Optional.empty();
                var pdf = workDir.resolve("source.pdf");
                return Files.exists(pdf) ? Optional.of(Files.readAllBytes(pdf)) : Optional.empty();
            } finally {
                deleteRecursively(workDir);
            }
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private boolean available() {
        var probe = available;
        if (probe == null) {
            synchronized (this) {
                probe = available;
                if (probe == null) {
                    probe = resolvedBinary() != null;
                    available = probe;
                }
            }
        }
        return probe;
    }

    private String resolvedBinary() {
        if (binary.contains(File.separator)) {
            return Files.isExecutable(Path.of(binary)) ? binary : null;
        }
        var path = System.getenv("PATH");
        if (path == null) return null;
        for (var directory : path.split(File.pathSeparator)) {
            var candidate = Path.of(directory).resolve(binary);
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        return null;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响预览结果
                }
            });
        }
    }
}
