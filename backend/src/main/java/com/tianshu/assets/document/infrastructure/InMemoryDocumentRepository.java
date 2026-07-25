package com.tianshu.assets.document.infrastructure;

import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentPage;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryDocumentRepository implements DocumentRepository {

    private final List<KnowledgeDocument> documents = new ArrayList<>();
    private final AtomicLong nextDocumentId = new AtomicLong(104);
    private final AtomicLong nextVersionId = new AtomicLong(1004);
    private final AtomicLong nextFileId = new AtomicLong(2004);

    public InMemoryDocumentRepository() {
        this(new InMemoryFileStorage());
    }

    @Autowired
    public InMemoryDocumentRepository(FileStorage fileStorage) {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        documents.add(seedDocument(101, "DOC-WI-000001", "焊接工位作业指导书",
                "焊接工位设备操作、安全检查和日常点检要求。", "WORK_INSTRUCTION",
                "陈工", "设备工程部", now.minus(3, ChronoUnit.DAYS),
                seedFile(fileStorage, 2001, "welding-instruction.pdf", "PDF", "application/pdf", "%PDF-1.7 demo")));
        documents.add(seedDocument(102, "DOC-TS-000002", "设备验收技术规范",
                "标准设备模块的到货、安装和精度验收要求。", "TECHNICAL_SPECIFICATION",
                "李工", "工艺仿真组", now.minus(1, ChronoUnit.DAYS),
                seedBinaryFile(fileStorage, 2002, "acceptance-checklist.png", "PNG", "image/png",
                        Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z3XcAAAAASUVORK5CYII="))));
        documents.add(new KnowledgeDocument(103, "DOC-DRAFT-000003", "待整理调试记录", "仅维护者可见的草稿。",
                "COMMISSIONING", "u-103", "王工", "自动化部", DocumentStatus.DRAFT, null,
                new DocumentVersion(1003, 103, "V1.0", "首次发布", DocumentVersionStatus.DRAFT,
                        List.of(seedFile(fileStorage, 2003, "commissioning-notes.docx", "DOCX",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "draft")),
                        "王工", now, "", null), now, now, 0));
    }

    @Override
    public synchronized DocumentPage searchPublished(DocumentSearchCriteria criteria) {
        var filtered = documents.stream()
                .filter(document -> document.status() == DocumentStatus.PUBLISHED)
                .filter(document -> matches(document, criteria))
                .sorted(Comparator.comparing(KnowledgeDocument::updatedAt).reversed())
                .toList();
        var offset = (long) (criteria.page() - 1) * criteria.perPage();
        return new DocumentPage(filtered.stream().skip(offset).limit(criteria.perPage()).toList(),
                filtered.size(), criteria.page(), criteria.perPage());
    }

    @Override
    public synchronized Optional<KnowledgeDocument> findById(long id) {
        return documents.stream().filter(document -> document.id() == id).findFirst();
    }

    @Override
    public synchronized boolean existsByDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) return false;
        return documents.stream().anyMatch(document -> document.documentNumber().equalsIgnoreCase(documentNumber.trim()));
    }

    @Override
    public synchronized KnowledgeDocument save(KnowledgeDocument document) {
        var documentId = nextDocumentId.getAndIncrement();
        var versionId = nextVersionId.getAndIncrement();
        var files = document.currentVersion().files().stream()
                .map(file -> withId(file, nextFileId.getAndIncrement()))
                .toList();
        var savedVersion = copyVersion(document.currentVersion(), versionId, documentId, files);
        var number = document.documentNumber().isBlank() ? "DOC-%06d".formatted(documentId) : document.documentNumber();
        var saved = new KnowledgeDocument(documentId, number, document.title(), document.summary(), document.categoryCode(),
                document.maintainerId(), document.maintainerName(), document.maintainerDepartment(), document.status(),
                document.status() == DocumentStatus.PUBLISHED ? versionId : null, savedVersion,
                document.createdAt(), document.updatedAt(), document.version());
        documents.add(saved);
        return saved;
    }

    @Override
    public synchronized KnowledgeDocument update(KnowledgeDocument document, long expectedVersion) {
        for (var index = 0; index < documents.size(); index++) {
            var current = documents.get(index);
            if (current.id() == document.id()) {
                if (current.version() != expectedVersion) {
                    throw new IllegalStateException("文档已被其他用户更新");
                }
                documents.set(index, document);
                return document;
            }
        }
        throw new IllegalArgumentException("文档不存在：" + document.id());
    }

    private boolean matches(KnowledgeDocument document, DocumentSearchCriteria criteria) {
        var query = criteria.query().toLowerCase(Locale.ROOT);
        var categoryMatches = criteria.categoryCode().isBlank()
                || document.categoryCode().equalsIgnoreCase(criteria.categoryCode());
        var queryMatches = query.isBlank()
                || document.documentNumber().toLowerCase(Locale.ROOT).contains(query)
                || document.title().toLowerCase(Locale.ROOT).contains(query)
                || document.summary().toLowerCase(Locale.ROOT).contains(query)
                || document.maintainerName().toLowerCase(Locale.ROOT).contains(query)
                || document.currentVersion().files().stream()
                        .anyMatch(file -> file.name().toLowerCase(Locale.ROOT).contains(query));
        return categoryMatches && queryMatches;
    }

    private KnowledgeDocument seedDocument(long id, String number, String title, String summary, String category,
            String maintainer, String department, Instant updatedAt, DocumentFile file) {
        var versionId = id + 900;
        return new KnowledgeDocument(id, number, title, summary, category, "u-" + id, maintainer, department,
                DocumentStatus.PUBLISHED, versionId,
                new DocumentVersion(versionId, id, "V1.0", "首次发布", DocumentVersionStatus.PUBLISHED,
                        List.of(file), maintainer, updatedAt, maintainer, updatedAt),
                updatedAt.minus(1, ChronoUnit.DAYS), updatedAt, 1);
    }

    private DocumentFile seedFile(FileStorage storage, long id, String name, String format,
            String contentType, String content) {
        return seedBinaryFile(storage, id, name, format, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private DocumentFile seedBinaryFile(FileStorage storage, long id, String name, String format,
            String contentType, byte[] bytes) {
        try {
            var key = storage.store(new ByteArrayInputStream(bytes), bytes.length, name, contentType);
            var stored = storage.open(key).orElseThrow();
            return new DocumentFile(id, name, format, bytes.length,
                    List.of("PDF", "PNG", "JPG", "JPEG", "TIFF").contains(format), key, stored.sha256());
        } catch (IOException exception) {
            throw new IllegalStateException("初始化内存文档文件失败", exception);
        }
    }

    private DocumentFile withId(DocumentFile file, long id) {
        return new DocumentFile(id, file.name(), file.format(), file.sizeBytes(), file.previewable(),
                file.storageKey(), file.contentSha256());
    }

    private DocumentVersion copyVersion(DocumentVersion version, long id, long documentId, List<DocumentFile> files) {
        return new DocumentVersion(id, documentId, version.versionNumber(), version.changeSummary(), version.status(),
                files, version.createdBy(), version.createdAt(), version.publishedBy(), version.publishedAt());
    }
}
