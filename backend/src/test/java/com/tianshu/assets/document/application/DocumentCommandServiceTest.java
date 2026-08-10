package com.tianshu.assets.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentScope;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentCommandServiceTest {

    private InMemoryFileStorage storage;
    private InMemoryDocumentRepository repository;
    private DocumentCommandService commands;

    @BeforeEach
    void setUp() {
        storage = new InMemoryFileStorage();
        repository = new InMemoryDocumentRepository(storage);
        commands = new DocumentCommandService(repository, storage);
    }

    @Test
    void generatesNumberAndPublishesTheFirstVersion() throws Exception {
        var draft = commands.createDraft(command("", pdfFile()));

        assertThat(draft.documentNumber()).matches("DOC-\\d{6}");
        assertThat(draft.currentVersion().versionNumber()).isEqualTo("V1.0");
        assertThat(draft.currentVersion().changeSummary()).isEqualTo("首次发布");

        var published = commands.publish(draft.id(), "u-100", "陈工");

        assertThat(published.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(published.currentVersionId()).isEqualTo(published.currentVersion().id());
        assertThat(published.currentVersion().status()).isEqualTo(DocumentVersionStatus.PUBLISHED);
        assertThat(published.currentVersion().publishedAt()).isNotNull();
        assertThat(published.version()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateManualNumbers() throws Exception {
        assertThatThrownBy(() -> commands.createDraft(command("doc-wi-000001", pdfFile())))
                .isInstanceOf(DuplicateDocumentNumberException.class);
    }

    @Test
    void rejectsPublishingAFileThatIsNoLongerStored() {
        var missingFile = new DocumentFile(0, "missing.pdf", "PDF", 100, true, "missing-key", "sha256");
        var draft = commands.createDraft(command("DOC-NEW-0001", missingFile));

        assertThatThrownBy(() -> commands.publish(draft.id(), "u-100", "陈工"))
                .isInstanceOf(DocumentPublishValidationException.class)
                .hasMessageContaining("文件");
    }

    @Test
    void rejectsRepeatedFirstPublication() throws Exception {
        var draft = commands.createDraft(command("DOC-NEW-0002", pdfFile()));
        commands.publish(draft.id(), "u-100", "陈工");

        assertThatThrownBy(() -> commands.publish(draft.id(), "u-100", "陈工"))
                .isInstanceOf(DocumentStateConflictException.class);
    }

    @Test
    void rejectsSpecifiedScopeWithoutItsRequiredProductionDimensions() throws Exception {
        var command = new CreateDocumentDraftCommand("DOC-NEW-0003", "测试文档", "用于应用服务测试。",
                "WORK_INSTRUCTION", "u-100", "陈工", "设备工程部", "", "", List.of(pdfFile()),
                DocumentScopeMode.SPECIFIED,
                List.of(new DocumentScope(0, 0, "乘用车", "大面水冷", "", "宁德基地", "A 拉线", "焊接段")));

        assertThatThrownBy(() -> commands.createDraft(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("平台族、蓝本、基地和拉线");
    }

    private CreateDocumentDraftCommand command(String number, DocumentFile file) {
        return new CreateDocumentDraftCommand(number, "测试文档", "用于应用服务测试。",
                "WORK_INSTRUCTION", "u-100", "陈工", "设备工程部", "", "", List.of(file));
    }

    private DocumentFile pdfFile() throws Exception {
        var bytes = "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8);
        var key = storage.store(new ByteArrayInputStream(bytes), bytes.length, "test.pdf", "application/pdf");
        var stored = storage.open(key).orElseThrow();
        return new DocumentFile(0, "test.pdf", "PDF", bytes.length, true, key, stored.sha256());
    }
}
