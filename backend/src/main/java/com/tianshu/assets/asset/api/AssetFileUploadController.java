package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetFileValidationException;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.common.file.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class AssetFileUploadController {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("EXE", "BAT", "CMD", "COM", "MSI", "SH", "JS", "JAR");
    private static final Set<String> PREVIEWABLE_FORMATS = Set.of("PDF", "PNG", "JPG", "JPEG", "TIFF", "DOCX", "DOC");
    private static final Set<String> IMAGE_PREVIEW_FORMATS = Set.of("PNG", "JPG", "JPEG", "TIFF");
    private final FileStorage storage;

    public AssetFileUploadController(FileStorage storage) {
        this.storage = storage;
    }

    @PostMapping("/files")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedFileResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new AssetFileValidationException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AssetFileValidationException("单个文件不能超过 500 MB");
        }
        var filename = file.getOriginalFilename();
        var format = format(filename);
        if (BLOCKED_EXTENSIONS.contains(format)) {
            throw new AssetFileValidationException("不允许上传可执行文件");
        }
        var bytes = file.getBytes();
        validateSignature(format, bytes);
        var storageKey = storage.store(new ByteArrayInputStream(bytes), bytes.length, filename, safeContentType(file.getContentType()));
        var assetFile = new AssetFile(0, filename, format, bytes.length, defaultRole(format), PREVIEWABLE_FORMATS.contains(format), false,
                storageKey, storage.open(storageKey).orElseThrow().sha256());
        return new UploadedFileResponse(assetFile);
    }

    private void validateSignature(String format, byte[] bytes) {
        if (format.equals("PDF") && !startsWith(bytes, "%PDF".getBytes())) {
            throw new AssetFileValidationException("文件扩展名与实际 PDF 内容不一致");
        }
        if (format.equals("PNG") && !(bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47)) {
            throw new AssetFileValidationException("文件扩展名与实际 PNG 内容不一致");
        }
        if ((format.equals("JPG") || format.equals("JPEG")) && !(bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff)) {
            throw new AssetFileValidationException("文件扩展名与实际 JPEG 内容不一致");
        }
        if (format.equals("DOCX") && !(bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B
                && bytes[2] == 0x03 && bytes[3] == 0x04)) {
            throw new AssetFileValidationException("文件扩展名与实际 DOCX 内容不一致");
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (var index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) return false;
        }
        return true;
    }

    private String format(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "OTHER" : filename.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private String defaultRole(String format) {
        if (Set.of("X_T", "STEP", "STP").contains(format)) return "三维源模型";
        if (IMAGE_PREVIEW_FORMATS.contains(format)) return "预览文件";
        if (format.equals("PDF")) return "二维图纸";
        if (Set.of("DWG", "DXF").contains(format)) return "二维图纸";
        return "其他附件";
    }

    private String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    public record UploadedFileResponse(AssetFile file) {}
}
