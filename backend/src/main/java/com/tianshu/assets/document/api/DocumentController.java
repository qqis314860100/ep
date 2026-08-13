package com.tianshu.assets.document.api;

import com.tianshu.assets.document.application.CreateDocumentDraftCommand;
import com.tianshu.assets.document.application.DocumentCommandService;
import com.tianshu.assets.document.application.DocumentQueryService;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentScope;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import com.tianshu.assets.common.preview.DocumentPreviewConverter;
import com.tianshu.assets.common.preview.NoopDocumentPreviewConverter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentCommandService commandService;
    private final DocumentQueryService queryService;
    private final DocumentPreviewConverter previewConverter;

    @Autowired
    public DocumentController(DocumentCommandService commandService, DocumentQueryService queryService,
            DocumentPreviewConverter previewConverter) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.previewConverter = previewConverter;
    }

    public DocumentController(DocumentCommandService commandService, DocumentQueryService queryService) {
        this(commandService, queryService, new NoopDocumentPreviewConverter());
    }

    @GetMapping
    public DocumentPageResponse search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "category", defaultValue = "") String category,
            @RequestParam(name = "platform_family", defaultValue = "") String platformFamily,
            @RequestParam(name = "platform_variant", defaultValue = "") String platformVariant,
            @RequestParam(name = "product_line", defaultValue = "") String productLine,
            @RequestParam(name = "base", defaultValue = "") String baseName,
            @RequestParam(name = "production_line", defaultValue = "") String productionLine,
            @RequestParam(name = "process_section", defaultValue = "") String processSection,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        var result = queryService.search(new DocumentSearchCriteria(query, category, platformFamily, platformVariant,
                productLine, baseName, productionLine, processSection, page, perPage));
        return new DocumentPageResponse(result.items().stream().map(DocumentResponse::from).toList(),
                PageMeta.of(result.total(), result.page(), result.perPage()));
    }

    @GetMapping("/{id}")
    public DocumentResponse detail(@PathVariable long id) {
        return DocumentResponse.from(queryService.getPublished(id));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDraft(@Valid @RequestBody CreateDraftRequest request) {
        return DocumentResponse.from(commandService.createDraft(request.toCommand()));
    }

    @PostMapping("/{id}/publish")
    public DocumentResponse publish(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String publisherId,
            @RequestHeader(name = "X-User-Name", defaultValue = "当前用户") String publisherName) {
        return DocumentResponse.from(commandService.publish(id, publisherId, publisherName));
    }

    @GetMapping("/{documentId}/versions/{versionId}/files/{fileId}")
    public ResponseEntity<byte[]> file(
            @PathVariable long documentId,
            @PathVariable long versionId,
            @PathVariable long fileId,
            @RequestParam(defaultValue = "false") boolean preview) {
        var access = queryService.openPublishedFile(documentId, versionId, fileId);
        if (preview && access.file().previewable() && previewConverter.supports(access.file().format())) {
            var converted = previewConverter.toPdf(access.file().format(), access.storedFile().content())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文档预览转换服务不可用"));
            var pdfName = access.file().name().replaceAll("(?i)\\.(docx|doc)$", "") + ".pdf";
            var pdfDisposition = ContentDisposition.inline().filename(pdfName, StandardCharsets.UTF_8).build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(converted.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, pdfDisposition.toString())
                    .body(converted);
        }
        var dispositionType = preview && access.file().previewable() ? "inline" : "attachment";
        var disposition = ContentDisposition.builder(dispositionType)
                .filename(access.file().name(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType(access.storedFile().contentType()))
                .contentLength(access.storedFile().size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(access.storedFile().content());
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record CreateDraftRequest(
            String documentNumber,
            @NotBlank(message = "请输入文档标题") String title,
            @NotBlank(message = "请输入文档摘要") String summary,
            @NotBlank(message = "请选择文档分类") String categoryCode,
            String maintainerId,
            @NotBlank(message = "请选择维护人") String maintainerName,
            String maintainerDepartment,
            String versionNumber,
            String changeSummary,
            @NotEmpty(message = "请至少上传一个文件") List<@Valid DocumentFileRequest> files,
            @NotNull(message = "请选择文档适用方式") DocumentScopeMode scopeMode,
            List<@Valid DocumentScopeRequest> scopes) {

        CreateDocumentDraftCommand toCommand() {
            return new CreateDocumentDraftCommand(documentNumber, title, summary, categoryCode, maintainerId,
                    maintainerName, maintainerDepartment, versionNumber, changeSummary,
                    files.stream().map(DocumentFileRequest::toDomain).toList(), scopeMode,
                    scopes == null ? List.of() : scopes.stream().map(DocumentScopeRequest::toDomain).toList());
        }
    }

    public record DocumentScopeRequest(
            String platformFamily,
            String platformVariant,
            String productLine,
            String baseName,
            String productionLine,
            String processSection) {
        DocumentScope toDomain() {
            return new DocumentScope(0, 0, platformFamily, platformVariant, productLine, baseName,
                    productionLine, processSection);
        }
    }

    public record DocumentFileRequest(
            @PositiveOrZero long id,
            @NotBlank(message = "文件名不能为空") String name,
            @NotBlank(message = "文件格式不能为空") String format,
            @PositiveOrZero long sizeBytes,
            boolean previewable,
            String storageKey,
            String contentSha256) {

        DocumentFile toDomain() {
            return new DocumentFile(id, name, format, sizeBytes, previewable, storageKey, contentSha256);
        }
    }

    public record DocumentPageResponse(List<DocumentResponse> data, PageMeta meta) {}

    public record PageMeta(long total, int page, int perPage, int totalPages) {
        static PageMeta of(long total, int page, int perPage) {
            return new PageMeta(total, page, perPage, perPage == 0 ? 0 : (int) Math.ceil((double) total / perPage));
        }
    }
}
