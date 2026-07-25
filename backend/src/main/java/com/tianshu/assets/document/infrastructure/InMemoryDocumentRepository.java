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

    private static final byte[] DEMO_PDF = Base64.getMimeDecoder().decode("""
            JVBERi0xLjQKJZOMi54gUmVwb3J0TGFiIEdlbmVyYXRlZCBQREYgZG9jdW1lbnQgKG9wZW5zb3VyY2UpCjEgMCBvYmoKPDwKL0Yx
            IDIgMCBSCj4+CmVuZG9iagoyIDAgb2JqCjw8Ci9CYXNlRm9udCAvSGVsdmV0aWNhIC9FbmNvZGluZyAvV2luQW5zaUVuY29kaW5n
            IC9OYW1lIC9GMSAvU3VidHlwZSAvVHlwZTEgL1R5cGUgL0ZvbnQKPj4KZW5kb2JqCjMgMCBvYmoKPDwKL0NvbnRlbnRzIDcgMCBS
            IC9NZWRpYUJveCBbIDAgMCA0ODAgMjcwIF0gL1BhcmVudCA2IDAgUiAvUmVzb3VyY2VzIDw8Ci9Gb250IDEgMCBSIC9Qcm9jU2V0
            IFsgL1BERiAvVGV4dCAvSW1hZ2VCIC9JbWFnZUMgL0ltYWdlSSBdCj4+IC9Sb3RhdGUgMCAvVHJhbnMgPDwKCj4+IAogIC9UeXBl
            IC9QYWdlCj4+CmVuZG9iago0IDAgb2JqCjw8Ci9QYWdlTW9kZSAvVXNlTm9uZSAvUGFnZXMgNiAwIFIgL1R5cGUgL0NhdGFsb2cK
            Pj4KZW5kb2JqCjUgMCBvYmoKPDwKL0F1dGhvciAoYW5vbnltb3VzKSAvQ3JlYXRpb25EYXRlIChEOjIwMDAwMTAxMDAwMDAwKzAw
            JzAwJykgL0NyZWF0b3IgKGFub255bW91cykgL0tleXdvcmRzICgpIC9Nb2REYXRlIChEOjIwMDAwMTAxMDAwMDAwKzAwJzAwJykg
            L1Byb2R1Y2VyIChSZXBvcnRMYWIgUERGIExpYnJhcnkgLSBcKG9wZW5zb3VyY2VcKSkgCiAgL1N1YmplY3QgKHVuc3BlY2lmaWVk
            KSAvVGl0bGUgKHVudGl0bGVkKSAvVHJhcHBlZCAvRmFsc2UKPj4KZW5kb2JqCjYgMCBvYmoKPDwKL0NvdW50IDEgL0tpZHMgWyAz
            IDAgUiBdIC9UeXBlIC9QYWdlcwo+PgplbmRvYmoKNyAwIG9iago8PAovRmlsdGVyIFsgL0FTQ0lJODVEZWNvZGUgL0ZsYXRlRGVj
            b2RlIF0gL0xlbmd0aCA5NzUKPj4Kc3RyZWFtCkdhczFeWiZUaGAlLipYJVdgLXRPOFplZj41YSFIb0kiMVZzLWNOXEpoIl5XZytm
            WCJFSkpcVlMlSk1qIitdbSsjPmtQO1JuKUhSb2NZclguKyJdRm9EZFJSTCw1TFcwcjswXEpsXkFXTD5oNiFoOENwLjU8clZmMnFD
            bzUhUlZDOkxtQFg9TFtvRCZxRE5dcU0zPS4zPGpRUlNIQFVUPEgha1ZiTilVZEE3cSJyQ2U5JGNERDdoWCsybnA+K2ZpMk5COUU9
            JTY6MUpdSTxRYyJxLSQodFI/TSpSWkY4YVhyPVdlQyRHRDFIOF5sNjcuWk9AJjJvPmwwcGFcS0ZUPEtBVDJxMDc/XixEMDw8XFpi
            J00zQSkhaFsycj5iRSFrO04rPz9xYEIjWUp0TmlwTzktXGRjK2I/NC9YTzxxa0JFZEVUUGhndWwsajE+Sz4tRTgvNkZnRDZLcEQq
            Wl9YRTgtRGNyJEs1cVBoXDE4aCk6MV0zUFkmQ2U8ITxYbDtNZjJlNFRMWGdMXjVRPWhdU2pgTVR1VFFGYWZfQzpmMz9rbzw3OmVZ
            Y08lODUzJjkvRkE9ZkBxXC0/LzZlN05BN3Nbc3BrNUJXNlsvI25dbk1QO11jSjYhV2JIKFEhYCcldXAxQzpybjtHJFE8LiN0PkNp
            PWhuTnM5OSZWWkBfLS4hXj1JJHM8W2VbWk5vbDpqWSQsPE5IOzZCN1Uwb2tiXVEuLTRGI25OY3NyXiQ8dUtkRE03bkM3ZURAKCw9
            OnVZLDVhMi8mJVw9VSlOPlRmREBUWnBSXERaSzxOSGYjckE7QXMlYjFWcmAvbGs5LiNLKl0iYS5JOW5wX0lGaEddKTNiLSVAKzRo
            cDA3ai9WPTJeWW1QWEpbRlUhbys5NXRGXGVrbUs7Ky9wPSdIVE8sKyQqTjE1Y1JcNHNadCVNaDUzOlUjIywkazY0cztlNiZvaVRA
            Sz00VEBEWS5XLDlrLSRDQk1zSl1hdXBcYWEnJFRoSERYOjgoSSMoNzBVU1QpaEQ7cEpXMVJfLUZDTS8ha2VqMmgxL2lYbW0iKS5t
            K2JRcVMxLVQwLyRWUW4qZk1hLEBPIUglSiRGaVVyTzAsIi8mZ3U1MXA0PS9XWSI8MjcvTy8qXjFXYi1tZF9pVEk8M1M0Vy9iUE9y
            RD1HaitgQFFbTG1GRV1GcztQLjxZdCZUT1ssRV1LTWk+S15dWktWIWBaay4mdWdLTl1oZC1oJmJrdWstaTxSYV9jVjVoL19SVTNz
            NVxFX1ZVRixjakwoZXFfPEZYcjtdJG9URWt+PmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDgKMDAwMDAwMDAwMCA2NTUzNSBmIAow
            MDAwMDAwMDYxIDAwMDAwIG4gCjAwMDAwMDAwOTIgMDAwMDAgbiAKMDAwMDAwMDE5OSAwMDAwMCBuIAowMDAwMDAwMzkyIDAwMDAw
            IG4gCjAwMDAwMDA0NjAgMDAwMDAgbiAKMDAwMDAwMDcyMSAwMDAwMCBuIAowMDAwMDAwNzgwIDAwMDAwIG4gCnRyYWlsZXIKPDwK
            L0lEIApbPDFjMTc4MTk4ZmJkZmE1MWIyNTk5ZWQ4OWQ0MTAyMDQzPjwxYzE3ODE5OGZiZGZhNTFiMjU5OTVkODlkNDEwMjA0Mz5d
            CiUgUmVwb3J0TGFiIGdlbmVyYXRlZCBQREYgZG9jdW1lbnQgLS0gZGlnZXN0IChvcGVuc291cmNlKQoKL0luZm8gNSAwIFIKL1Jv
            b3QgNCAwIFIKL1NpemUgOAo+PgpzdGFydHhyZWYKMTg0NQolJUVPRgo=
            """);
    private static final byte[] DEMO_IMAGE = Base64.getMimeDecoder().decode("""
            iVBORw0KGgoAAAANSUhEUgAAAUAAAAC0CAIAAABqhmJGAAAC9klEQVR42u3dsU4qURSG0cNkCh/HEp/DgsrezsLSGMcYSws7eyoK
            nkNL3wZCLOwIJZg5eGbvtbp7zb3CDx9DEJnZZrctwDR1JgABAwIGBAwCBgQMCBgQMAgYEDAgYBAwIGBAwICAIbr+1H8wf7izGtTz
            9fruCAyeQgMCBgQMCBgEDAgYEDAIGBAwIGBAwCBgQMCAgAEBQwQzJ/gGR2BAwICAQcCAgAEBAwIGAQMCBgQMAgYEDAgYEDAIGBAw
            ML6+9jdYrldWJrOb60XxiRyAp9AgYEDAgIBBwICAAQEDAgYBAwIGBAwIGAQMCBgQMAgYEDAgYKD842di3d7fxZjp4+3dPvZpbZ+6
            R+Aw61e6LvaxT2n2Q+32l3h4Gqa+/vA8jP44ah/7tHsEjrT+4bUY63HUPvaZwFPoGOvXuy72sY9XoaH4MRIgYEDAgIBBwICAAQED
            AgYBAwIGBAwCBgQMCBgQMAgYEDAgYKCc4XOh6170z5fDP/5cPbo57ZNtnz7A9Id/6W5qn1T7dDHWP/Krae+d9om6TxdpffdR+2Tb
            pwu2fub7qH0S7uNVaCh+jNTQw2fOg4x9cu7TF472fVHmD2Oc3uaiXG7tk24fT6EBR+C/utyOc4bISGe+tY8jMBA94FPfIpPtLUf2
            ybmPIzA4Ajf2IJrzHb/2SbhPF+82yPx+fftk26cLdhv4bRv7pNqnn+5t4Pdd7WOfPs/rig41xNvHq9AgYEDAgIBBwICAAQEDAgYB
            AwIGBAwCBgQMCBgQMAgYEDAwrYCH5yHMTDWui33s02jA+/NrxLgN9tdilPOG2Mc+I5ptdrXOAxfsDDejr28f+zT9FLrSJQ6zvn3s
            0/QRGChehQYEDAIGBAwIGAQMCBgQMCBgEDAgYEDAgIBBwICAAQGDgAEBA2fU1/4Gy/XKymR2c72o95/7TCzwFBoQMCBgEDAgYEDA
            gIBBwICAAQGDgAEBAwIGBAwCBgQMCBgQMAgYEDAgYBAwIGBAwICAQcBAg34BElkKnmOeCTEAAAAASUVORK5CYII=
            """);

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
                seedBinaryFile(fileStorage, 2001, "welding-instruction.pdf", "PDF", "application/pdf", DEMO_PDF)));
        documents.add(seedDocument(102, "DOC-TS-000002", "设备验收技术规范",
                "标准设备模块的到货、安装和精度验收要求。", "TECHNICAL_SPECIFICATION",
                "李工", "工艺仿真组", now.minus(1, ChronoUnit.DAYS),
                seedBinaryFile(fileStorage, 2002, "acceptance-checklist.png", "PNG", "image/png", DEMO_IMAGE)));
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
