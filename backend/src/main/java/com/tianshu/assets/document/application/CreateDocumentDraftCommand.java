package com.tianshu.assets.document.application;

import com.tianshu.assets.document.domain.DocumentFile;
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
        List<DocumentFile> files) {

    public CreateDocumentDraftCommand {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
