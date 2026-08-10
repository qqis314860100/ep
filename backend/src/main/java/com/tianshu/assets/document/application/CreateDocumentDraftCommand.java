package com.tianshu.assets.document.application;

import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentScope;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import java.util.List;

public record CreateDocumentDraftCommand(
        String documentNumber,
        String title,
        String summary,
        String categoryCode,
        String maintainerId,
        String maintainerName,
        String maintainerDepartment,
        String versionNumber,
        String changeSummary,
        List<DocumentFile> files,
        DocumentScopeMode scopeMode,
        List<DocumentScope> scopes) {

    public CreateDocumentDraftCommand {
        files = files == null ? List.of() : List.copyOf(files);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public CreateDocumentDraftCommand(String documentNumber, String title, String summary, String categoryCode,
            String maintainerId, String maintainerName, String maintainerDepartment, String versionNumber,
            String changeSummary, List<DocumentFile> files) {
        this(documentNumber, title, summary, categoryCode, maintainerId, maintainerName, maintainerDepartment,
                versionNumber, changeSummary, files, DocumentScopeMode.GLOBAL, List.of());
    }
}
