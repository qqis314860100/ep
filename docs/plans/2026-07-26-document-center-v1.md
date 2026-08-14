# Document Center V1.6.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first document-center loop in which a maintainer creates and publishes a document with its first file version, and users search, preview, and download published documents.

**Architecture:** Add an independent `document` bounded context with domain, application, infrastructure, and API packages. Move the existing file-storage port and stored-file value into `common.file` so assets and documents share storage without depending on each other; keep the default `dev` profile in memory, add `local` JDBC persistence, and keep `oceanbase` writes explicitly read-only. The frontend uses three route-level pages backed by one typed service: a compact search workspace, a single-page draft/publish form, and a current-version detail workspace.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring MVC Test, Spring JDBC, MySQL/OceanBase-compatible SQL, React 18, TypeScript, Vite 8, Ant Design 5, styled-components, TanStack Query, Vitest, Testing Library.

---

## File Map

### Shared backend file boundary

- Create `backend/src/main/java/com/tianshu/assets/common/file/FileStorage.java`: profile-neutral file storage port and `StoredFile` value.
- Move/rename `backend/src/main/java/com/tianshu/assets/asset/infrastructure/InMemoryAssetFileStorage.java` to `backend/src/main/java/com/tianshu/assets/common/file/InMemoryFileStorage.java`: `dev` adapter.
- Move/rename `backend/src/main/java/com/tianshu/assets/asset/infrastructure/LocalAssetFileStorage.java` to `backend/src/main/java/com/tianshu/assets/common/file/LocalFileStorage.java`: `local` adapter.
- Move/rename `backend/src/main/java/com/tianshu/assets/asset/infrastructure/ReadOnlyAssetFileStorage.java` to `backend/src/main/java/com/tianshu/assets/common/file/ReadOnlyFileStorage.java`: `oceanbase` adapter.
- Modify `backend/src/main/java/com/tianshu/assets/asset/api/AssetFileUploadController.java`: depend on `FileStorage` while preserving `/api/v1/uploads/files` and its response.
- Modify asset controllers/services that open files: replace `AssetFileStorage` imports with `FileStorage` without changing asset behavior.
- Delete `backend/src/main/java/com/tianshu/assets/asset/application/AssetFileStorage.java` after all callers compile.

### Document backend

- Create `backend/src/main/java/com/tianshu/assets/document/domain/DocumentStatus.java` and `DocumentVersionStatus.java`: lifecycle enums.
- Create `backend/src/main/java/com/tianshu/assets/document/domain/DocumentFile.java`: immutable uploaded-file reference.
- Create `backend/src/main/java/com/tianshu/assets/document/domain/DocumentVersion.java`: first version and its files.
- Create `backend/src/main/java/com/tianshu/assets/document/domain/KnowledgeDocument.java`: aggregate root.
- Create `backend/src/main/java/com/tianshu/assets/document/domain/DocumentSearchCriteria.java` and `DocumentPage.java`: query values.
- Create `backend/src/main/java/com/tianshu/assets/document/domain/DocumentRepository.java`: aggregate persistence contract.
- Create `backend/src/main/java/com/tianshu/assets/document/application/DocumentCommandService.java`: create-draft and first-publish use cases.
- Create `backend/src/main/java/com/tianshu/assets/document/application/DocumentQueryService.java`: published search, detail, and file access use cases.
- Create document exceptions under `document/application`: not found, duplicate number, invalid state, and invalid publish data.
- Create `backend/src/main/java/com/tianshu/assets/document/infrastructure/InMemoryDocumentRepository.java`: `dev` repository with deterministic seed documents.
- Create `backend/src/main/java/com/tianshu/assets/document/infrastructure/JdbcDocumentRepository.java`: `local` repository with transactional aggregate writes.
- Create `backend/src/main/java/com/tianshu/assets/document/infrastructure/ReadOnlyDocumentRepository.java`: `oceanbase` query adapter with explicit write rejection.
- Create `backend/src/main/java/com/tianshu/assets/document/api/DocumentController.java`: REST endpoints and file responses.
- Create `backend/src/main/java/com/tianshu/assets/document/api/DocumentResponse.java`: stable JSON mapping.
- Modify `backend/src/main/java/com/tianshu/assets/common/api/ApiExceptionHandler.java`: document-specific 404/409/422 errors.
- Modify `backend/src/main/java/com/tianshu/assets/dictionary/application/DictionaryService.java`: declare `DOCUMENT_CATEGORY` and six initial values for the in-memory profile.
- Create `docs/migrations/V1_6__document_center_schema.sql`: controlled production-compatible DDL.
- Modify `docs/migrations/local/V1_5__local_bootstrap.sql`: append idempotent local document tables.
- Modify `docs/migrations/local/V1_5__local_seed.sql`: append idempotent document category seed values.

### Document frontend

- Modify `frontend/package.json` and `frontend/pnpm-lock.yaml`: add Vitest, jsdom, Testing Library, and test scripts.
- Create `frontend/src/test/setup.ts`: DOM matchers and browser API cleanup.
- Modify `frontend/vite.config.ts`: add Vitest configuration.
- Create `frontend/src/types/document.ts`: API and form types.
- Create `frontend/src/services/documentService.ts`: list/detail/draft/publish/upload/file URL functions and API error mapping.
- Create `frontend/src/features/documents/documentPresentation.ts`: category/status/file display helpers.
- Create `frontend/src/features/documents/useDocumentSearch.ts`: URL-backed query/filter/page state and TanStack Query call.
- Create `frontend/src/features/documents/DocumentSearchPage.tsx`: compact search workspace.
- Create `frontend/src/features/documents/DocumentCreatePage.tsx`: one-page Ant Design form, upload queue, draft save, publish confirmation, and dirty-leave guard.
- Create `frontend/src/pages/main/document-detail/index.tsx`: current-version detail workspace and preview/download behavior.
- Create focused component files under `frontend/src/features/documents/components/` for category navigation, result list, document form, and file list/preview.
- Create service, search-state, and form tests under `frontend/src/features/documents/__tests__/`.
- Modify `frontend/src/App.tsx`: lazy document routes.
- Modify `frontend/src/app/AppShell.tsx`: primary “文档中心” navigation entry and route-aware module name.

## Task 1: Extract the Shared File Storage Port

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/common/file/FileStorage.java`
- Create: `backend/src/main/java/com/tianshu/assets/common/file/InMemoryFileStorage.java`
- Create: `backend/src/main/java/com/tianshu/assets/common/file/LocalFileStorage.java`
- Create: `backend/src/main/java/com/tianshu/assets/common/file/ReadOnlyFileStorage.java`
- Modify: `backend/src/main/java/com/tianshu/assets/asset/api/AssetFileUploadController.java`
- Modify: every existing Java caller found by `rg -l 'AssetFileStorage|StoredAssetFile' backend/src/main/java backend/src/test/java`
- Delete: `backend/src/main/java/com/tianshu/assets/asset/application/AssetFileStorage.java`
- Delete: the three superseded `*AssetFileStorage.java` adapters
- Test: `backend/src/test/java/com/tianshu/assets/asset/api/AssetFileUploadControllerTest.java`

- [ ] **Step 1: Update the upload controller test to construct the common storage adapter**

```java
import com.tianshu.assets.common.file.InMemoryFileStorage;

var storage = new InMemoryFileStorage();
mockMvc = standaloneSetup(new AssetFileUploadController(storage))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
```

- [ ] **Step 2: Run the focused test and verify the missing common type fails compilation**

Run: `cd backend && rtk mvn -Dtest=AssetFileUploadControllerTest test`

Expected: FAIL because `com.tianshu.assets.common.file.InMemoryFileStorage` does not exist.

- [ ] **Step 3: Create the common port and rename the adapters**

```java
package com.tianshu.assets.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface FileStorage {
    String store(InputStream content, long size, String originalFilename, String contentType) throws IOException;
    Optional<StoredFile> open(String storageKey);

    record StoredFile(String storageKey, String originalFilename, String contentType,
            long size, String sha256, byte[] content) {}
}
```

Preserve each current adapter's byte, metadata, SHA-256, profile, and read-only behavior; only rename the class/package and implement `FileStorage`.

- [ ] **Step 4: Replace asset imports and verify no obsolete storage reference remains**

Run: `rg -n 'AssetFileStorage|StoredAssetFile' backend/src/main/java backend/src/test/java`

Expected: no matches.

- [ ] **Step 5: Run the focused upload tests**

Run: `cd backend && rtk mvn -Dtest=AssetFileUploadControllerTest test`

Expected: PASS with all existing extension, signature, size, and response tests unchanged.

## Task 2: Define the Document Domain and In-Memory Repository

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentVersionStatus.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentFile.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentVersion.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/KnowledgeDocument.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentSearchCriteria.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentPage.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentRepository.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/infrastructure/InMemoryDocumentRepository.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/infrastructure/InMemoryDocumentRepositoryTest.java`

- [ ] **Step 1: Write repository tests for published-only search, category filtering, filename search, paging, and number uniqueness**

```java
@Test
void searchesOnlyPublishedDocumentsAcrossMetadataAndFiles() {
    var repository = new InMemoryDocumentRepository();
    var page = repository.searchPublished(new DocumentSearchCriteria("焊接", "", 1, 20));
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().getFirst().status()).isEqualTo(DocumentStatus.PUBLISHED);
}

@Test
void filtersCategoryAndKeepsStablePaging() {
    var page = new InMemoryDocumentRepository()
            .searchPublished(new DocumentSearchCriteria("", "WORK_INSTRUCTION", 1, 1));
    assertThat(page.items()).hasSize(1);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.perPage()).isEqualTo(1);
}
```

- [ ] **Step 2: Run the repository test and verify it fails**

Run: `cd backend && rtk mvn -Dtest=InMemoryDocumentRepositoryTest test`

Expected: FAIL because the document domain and repository do not exist.

- [ ] **Step 3: Add lifecycle enums and immutable records**

```java
public enum DocumentStatus { DRAFT, PUBLISHED, DISABLED }
public enum DocumentVersionStatus { DRAFT, PUBLISHED, HISTORICAL }

public record DocumentFile(long id, String name, String format, long sizeBytes,
        boolean previewable, String storageKey, String contentSha256) {}

public record DocumentVersion(long id, long documentId, String versionNumber,
        String changeSummary, DocumentVersionStatus status, List<DocumentFile> files,
        String createdBy, Instant createdAt, String publishedBy, Instant publishedAt) {}
```

`KnowledgeDocument` must expose exactly the design fields and defensively copy its current version/files. `DocumentSearchCriteria` must normalize null values, clamp `page >= 1`, and clamp `1 <= perPage <= 100`.

- [ ] **Step 4: Add the repository contract**

```java
public interface DocumentRepository {
    DocumentPage searchPublished(DocumentSearchCriteria criteria);
    Optional<KnowledgeDocument> findById(long id);
    boolean existsByDocumentNumber(String documentNumber);
    KnowledgeDocument save(KnowledgeDocument document);
    KnowledgeDocument update(KnowledgeDocument document, long expectedVersion);
}
```

- [ ] **Step 5: Implement deterministic `dev` data and synchronized in-memory writes**

Seed at least one published PDF work instruction and one published image technical specification, plus one draft hidden from search. Assign IDs atomically, generate `DOC-%06d` after the document ID exists, assign file/version IDs, and reject version mismatches instead of silently overwriting.

- [ ] **Step 6: Run repository tests**

Run: `cd backend && rtk mvn -Dtest=InMemoryDocumentRepositoryTest test`

Expected: PASS.

## Task 3: Implement Document Commands and Queries

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/document/application/CreateDocumentDraftCommand.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DocumentCommandService.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DocumentQueryService.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DocumentNotFoundException.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DuplicateDocumentNumberException.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DocumentStateConflictException.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/application/DocumentPublishValidationException.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/application/DocumentCommandServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/application/DocumentQueryServiceTest.java`

- [ ] **Step 1: Write command tests for automatic number, manual duplicate, incomplete publish, successful publish, and repeated publish**

```java
@Test
void generatesNumberAndPublishesTheFirstVersion() {
    var draft = commands.createDraft(command("", "作业指导书", List.of(pdfFile())));
    assertThat(draft.documentNumber()).matches("DOC-\\d{6}");
    var published = commands.publish(draft.id(), "u-100", "陈工");
    assertThat(published.status()).isEqualTo(DocumentStatus.PUBLISHED);
    assertThat(published.currentVersion().status()).isEqualTo(DocumentVersionStatus.PUBLISHED);
    assertThat(published.currentVersion().publishedAt()).isNotNull();
}

@Test
void rejectsPublishingAnIncompleteOrPublishedDocument() {
    var incomplete = commands.createDraft(command("DOC-TEST-1", "", List.of(pdfFile())));
    assertThatThrownBy(() -> commands.publish(incomplete.id(), "u-100", "陈工"))
            .isInstanceOf(DocumentPublishValidationException.class);
}
```

- [ ] **Step 2: Run command tests and verify they fail**

Run: `cd backend && rtk mvn -Dtest=DocumentCommandServiceTest test`

Expected: FAIL because the application services do not exist.

- [ ] **Step 3: Implement the draft command and command service**

```java
public record CreateDocumentDraftCommand(String documentNumber, String title, String summary,
        String categoryCode, String maintainerId, String maintainerName,
        String maintainerDepartment, String versionNumber, String changeSummary,
        List<DocumentFile> files) {}
```

`createDraft` trims strings, requires title/category/summary/maintainer name/files, defaults version to `V1.0`, defaults change summary to `首次发布`, rejects a supplied duplicate number, creates `DRAFT` document/version state, then relies on the repository to assign IDs and an automatic number. `publish` reloads the aggregate, rejects non-draft state, revalidates all publish fields and file storage keys, atomically changes both statuses, sets `currentVersionId`, timestamps publication, and increments the aggregate version.

- [ ] **Step 4: Implement query tests and service**

```java
@Test
void opensOnlyFilesBelongingToTheRequestedCurrentVersion() {
    var access = queries.openPublishedFile(documentId, versionId, fileId);
    assertThat(access.document().status()).isEqualTo(DocumentStatus.PUBLISHED);
    assertThat(access.storedFile().content()).isNotEmpty();
}
```

`DocumentQueryService` delegates published search, rejects draft/disabled detail access, confirms the requested version/file belongs to the document, and then opens the `storageKey` through `FileStorage`. A missing document, version, file, or stored object throws `DocumentNotFoundException` without leaking partial metadata.

- [ ] **Step 5: Run application tests**

Run: `cd backend && rtk mvn -Dtest='DocumentCommandServiceTest,DocumentQueryServiceTest' test`

Expected: PASS.

## Task 4: Expose and Test the Document REST API

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/document/api/DocumentController.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/api/DocumentResponse.java`
- Modify: `backend/src/main/java/com/tianshu/assets/common/api/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/api/DocumentControllerTest.java`

- [ ] **Step 1: Write controller tests for the complete V1.6.1 contract**

```java
@Test
void createsPublishesSearchesAndReadsTheCurrentDocument() throws Exception {
    var draftJson = mockMvc.perform(post("/api/v1/documents/drafts")
            .contentType(APPLICATION_JSON)
            .content(validDraftJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andReturn().getResponse().getContentAsString();
    var id = objectMapper.readTree(draftJson).get("id").asLong();

    mockMvc.perform(post("/api/v1/documents/{id}/publish", id)
            .header("X-User-Id", "u-100").header("X-User-Name", "陈工"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    mockMvc.perform(get("/api/v1/documents").param("q", "测试文档"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.total").value(1));
}
```

Also assert `400 validation_error`, `404 document_not_found`, `409 duplicate_document_number`, `409 document_state_conflict`, and `422 document_publish_invalid`.

- [ ] **Step 2: Run controller tests and verify they fail**

Run: `cd backend && rtk mvn -Dtest=DocumentControllerTest test`

Expected: FAIL because the controller and response types do not exist.

- [ ] **Step 3: Implement request validation and response mapping**

```java
public record CreateDraftRequest(
        String documentNumber,
        @NotBlank String title,
        @NotBlank String summary,
        @NotBlank String categoryCode,
        String maintainerId,
        @NotBlank String maintainerName,
        String maintainerDepartment,
        String versionNumber,
        String changeSummary,
        @NotEmpty List<DocumentFileRequest> files) {}
```

Return `PageResponse<DocumentResponse>` from `GET /api/v1/documents`; return `201` from draft creation and `200` from publish/detail. Map JSON names in camelCase while accepting query names `q`, `category`, `page`, and `per_page`.

- [ ] **Step 4: Implement guarded file streaming**

Use `Content-Disposition: inline` only when `preview=true` and the document file is previewable; otherwise use `attachment`. Always set the stored content type, content length, and RFC 5987 UTF-8 filename. The controller must obtain bytes only through `DocumentQueryService.openPublishedFile`.

- [ ] **Step 5: Add document exception mappings**

```java
document_not_found             -> 404
duplicate_document_number      -> 409
document_state_conflict        -> 409
document_publish_invalid       -> 422
```

- [ ] **Step 6: Run the controller tests**

Run: `cd backend && rtk mvn -Dtest=DocumentControllerTest test`

Expected: PASS.

## Task 5: Add JDBC Persistence, Dictionary Values, and Controlled DDL

**Files:**
- Create: `backend/src/main/java/com/tianshu/assets/document/infrastructure/JdbcDocumentRepository.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/infrastructure/ReadOnlyDocumentRepository.java`
- Modify: `backend/src/main/java/com/tianshu/assets/dictionary/application/DictionaryService.java`
- Create: `backend/src/test/java/com/tianshu/assets/document/infrastructure/JdbcDocumentRepositoryTest.java`
- Modify: `backend/src/test/java/com/tianshu/assets/dictionary/api/DictionaryControllerTest.java`
- Create: `docs/migrations/V1_6__document_center_schema.sql`
- Modify: `docs/migrations/local/V1_5__local_bootstrap.sql`
- Modify: `docs/migrations/local/V1_5__local_seed.sql`

- [ ] **Step 1: Add failing dictionary and JDBC contract tests**

```java
@Test
void exposesDocumentCategories() throws Exception {
    mockMvc.perform(get("/api/v1/dictionaries/items").param("category", "DOCUMENT_CATEGORY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("技术规范"))
        .andExpect(jsonPath("$[5].label").value("标准模板"));
}
```

The JDBC test uses an isolated test datasource/schema, saves a draft containing two files, reloads it, publishes with an expected aggregate version, verifies all rows are present, and verifies a stale update affects zero rows and raises a state conflict.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `cd backend && rtk mvn -Dtest='DictionaryControllerTest,JdbcDocumentRepositoryTest' test`

Expected: FAIL because the category and JDBC repository are missing.

- [ ] **Step 3: Add `DOCUMENT_CATEGORY` metadata and initial values**

Use stable codes and labels:

```text
TECHNICAL_SPECIFICATION = 技术规范
MANUAL                  = 说明书
WORK_INSTRUCTION        = 作业指导书
COMMISSIONING           = 调试资料
ACCEPTANCE              = 验收资料
STANDARD_TEMPLATE       = 标准模板
```

- [ ] **Step 4: Implement transactional JDBC aggregate persistence**

Use `NamedParameterJdbcTemplate` or `JdbcTemplate` consistently with the existing repository. Insert `knowledge_document`, then `document_version`, then batch insert `document_file` in one transaction. Publish using `UPDATE knowledge_document ... WHERE id = ? AND version = ? AND status = 'DRAFT'`; only after that succeeds update the version to `PUBLISHED`. Queries must join/load only the requested page of document IDs to avoid duplicate pagination rows.

- [ ] **Step 5: Add exact controlled DDL**

```sql
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_number VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    maintainer_id VARCHAR(64) NOT NULL DEFAULT '',
    maintainer_name VARCHAR(100) NOT NULL,
    maintainer_department VARCHAR(100) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    current_version_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_number (document_number),
    KEY idx_knowledge_document_search (status, category_code, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Add `document_version` with `UNIQUE(document_id, version_number)` and `document_file` with foreign-key-compatible indexes. Do not alter a legacy primary key or overwrite a legacy value. The formal migration and local bootstrap must define equivalent columns.

- [ ] **Step 6: Add the read-only production adapter**

`ReadOnlyDocumentRepository` may execute compatible document queries only after the V1.6 schema exists; `save` and `update` always throw `UnsupportedOperationException("OceanBase 文档仓储当前为只读")`.

- [ ] **Step 7: Run JDBC and dictionary tests**

Run: `cd backend && rtk mvn -Dtest='DictionaryControllerTest,JdbcDocumentRepositoryTest' test`

Expected: PASS.

## Task 6: Verify and Commit Backend V1.6.1

**Files:**
- Review: all backend and migration files from Tasks 1-5

- [ ] **Step 1: Run focused document and upload tests**

Run: `cd backend && rtk mvn -Dtest='AssetFileUploadControllerTest,DocumentControllerTest,DocumentCommandServiceTest,DocumentQueryServiceTest,InMemoryDocumentRepositoryTest,JdbcDocumentRepositoryTest,DictionaryControllerTest' test`

Expected: PASS.

- [ ] **Step 2: Run the complete backend suite**

Run: `cd backend && rtk mvn test`

Expected: BUILD SUCCESS with no failed tests.

- [ ] **Step 3: Review only the backend/migration diff**

Run: `rtk git diff -- backend docs/migrations`

Expected: no production credentials, no legacy key changes, no lifecycle beyond `DRAFT/PUBLISHED/DISABLED`, and no V1.6.2 association/version-history implementation.

- [ ] **Step 4: Commit the backend version**

```bash
git add backend docs/migrations
git commit -m "feat(后端): 实现文档草稿与首次发布" -m "产品版本：V1.6.1"
```

## Task 7: Install and Prove the Frontend Test Harness

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/features/documents/__tests__/testHarness.test.tsx`

- [ ] **Step 1: Add the test script before dependencies and verify it cannot run**

```json
"test": "vitest run",
"test:watch": "vitest"
```

Run: `cd frontend && rtk pnpm test`

Expected: FAIL because `vitest` is not installed.

- [ ] **Step 2: Install the exact testing toolchain with pnpm**

Run: `cd frontend && rtk pnpm add -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event`

Expected: `package.json` and `pnpm-lock.yaml` updated; no `package-lock.json` created.

- [ ] **Step 3: Configure jsdom and a proof test**

```ts
// vite.config.ts
test: {
  environment: 'jsdom',
  setupFiles: './src/test/setup.ts',
  css: true,
}
```

```tsx
it('renders the test environment', () => {
  render(<button type="button">新建文档</button>)
  expect(screen.getByRole('button', { name: '新建文档' })).toBeInTheDocument()
})
```

- [ ] **Step 4: Run the proof test**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/testHarness.test.tsx`

Expected: PASS.

## Task 8: Implement Typed Document API Access

**Files:**
- Create: `frontend/src/types/document.ts`
- Create: `frontend/src/services/documentService.ts`
- Test: `frontend/src/features/documents/__tests__/documentService.test.ts`

- [ ] **Step 1: Write failing service tests**

```ts
it('serializes supported search filters only', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok(pagePayload)))
  await searchDocuments({ query: '焊接', category: 'WORK_INSTRUCTION', page: 2, perPage: 20 })
  expect(fetch).toHaveBeenCalledWith(
    '/api/v1/documents?q=%E7%84%8A%E6%8E%A5&category=WORK_INSTRUCTION&page=2&per_page=20',
    expect.any(Object),
  )
})

it('surfaces the backend error message', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(error(409, '文档编号已存在')))
  await expect(publishDocument(1)).rejects.toThrow('文档编号已存在')
})
```

- [ ] **Step 2: Run service tests and verify they fail**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/documentService.test.ts`

Expected: FAIL because the types and service do not exist.

- [ ] **Step 3: Define types matching the backend response exactly**

```ts
export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type DocumentVersionStatus = 'DRAFT' | 'PUBLISHED' | 'HISTORICAL'

export interface KnowledgeDocument {
  id: number
  documentNumber: string
  title: string
  summary: string
  categoryCode: string
  maintainerId: string
  maintainerName: string
  maintainerDepartment: string
  status: DocumentStatus
  currentVersionId?: number
  currentVersion: DocumentVersion
  createdAt: string
  updatedAt: string
  version: number
}
```

Add `DocumentFile`, `DocumentVersion`, `DocumentPage`, `DocumentSearchParams`, and `CreateDocumentDraftInput` with the same camelCase field names.

- [ ] **Step 4: Implement requests, upload reuse, and guarded file URLs**

`searchDocuments`, `getDocument`, `createDocumentDraft`, and `publishDocument` use JSON APIs. `uploadDocumentFile` calls the existing `/api/v1/uploads/files` and maps its asset-shaped file description to `DocumentFile`. `getDocumentFileUrl(documentId, versionId, fileId, preview)` returns only the guarded document resource URL.

- [ ] **Step 5: Run service tests**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/documentService.test.ts`

Expected: PASS.

## Task 9: Build the Document Search Workspace

**Files:**
- Create: `frontend/src/features/documents/documentPresentation.ts`
- Create: `frontend/src/features/documents/useDocumentSearch.ts`
- Create: `frontend/src/features/documents/components/DocumentCategoryNav.tsx`
- Create: `frontend/src/features/documents/components/DocumentResultList.tsx`
- Create: `frontend/src/features/documents/DocumentSearchPage.tsx`
- Test: `frontend/src/features/documents/__tests__/DocumentSearchPage.test.tsx`

- [ ] **Step 1: Write failing behavior tests**

```tsx
it('searches from the workspace and keeps category visible', async () => {
  renderPage('/documents?category=WORK_INSTRUCTION&q=焊接')
  expect(await screen.findByText('焊接工位作业指导书')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /作业指导书/ })).toHaveAttribute('aria-current', 'page')
  await user.click(screen.getByRole('button', { name: '清空筛选' }))
  expect(screen.getByRole('searchbox')).toHaveValue('')
})

it('separates loading failure and empty results', async () => {
  server.use(failingSearchHandler)
  renderPage('/documents')
  expect(await screen.findByRole('button', { name: '重新加载' })).toBeInTheDocument()
})
```

- [ ] **Step 2: Run the page test and verify it fails**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentSearchPage.test.tsx`

Expected: FAIL because the search workspace does not exist.

- [ ] **Step 3: Implement URL-backed search state**

`useDocumentSearch` reads `q`, `category`, and `page` from `useSearchParams`, resets page to 1 when query/category changes, requests 20 rows through TanStack Query, and leaves unsupported maintainer/time controls absent.

- [ ] **Step 4: Implement the compact two-column workspace**

Use a stable 208-224 px category rail and a minmax results column. The toolbar contains a standard search input, result count, and primary “新建文档” button. Each row shows title, document number, category, summary, current version, maintainer, update time, and preview marker with a stable row height; selecting a row navigates to `/documents/:id`.

- [ ] **Step 5: Implement explicit states**

Loading uses a compact skeleton in the result area. Network failure keeps the current URL filters and offers “重新加载”. Empty search offers “清空筛选”; an empty category with no query keeps the category selected and explains there are no published documents in that category.

- [ ] **Step 6: Run the page tests**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentSearchPage.test.tsx`

Expected: PASS.

## Task 10: Build the Single-Page Draft and Publish Workflow

**Files:**
- Create: `frontend/src/features/documents/components/DocumentFileList.tsx`
- Create: `frontend/src/features/documents/components/DocumentForm.tsx`
- Create: `frontend/src/features/documents/DocumentCreatePage.tsx`
- Test: `frontend/src/features/documents/__tests__/DocumentCreatePage.test.tsx`

- [ ] **Step 1: Write failing form workflow tests**

```tsx
it('requires minimum metadata and one uploaded file', async () => {
  renderCreatePage()
  await user.click(screen.getByRole('button', { name: '保存草稿' }))
  expect(await screen.findByText('请输入文档标题')).toBeInTheDocument()
  expect(screen.getByText('请至少上传一个文件')).toBeInTheDocument()
})

it('saves a draft and publishes only after confirmation', async () => {
  renderCreatePage()
  await fillMinimumFormAndUploadPdf(user)
  await user.click(screen.getByRole('button', { name: '发布文档' }))
  expect(await screen.findByText('确认首次发布')).toBeInTheDocument()
  expect(screen.getByText('V1.0')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '确认发布' }))
  expect(publishDocument).toHaveBeenCalledWith(321)
})
```

- [ ] **Step 2: Run form tests and verify they fail**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentCreatePage.test.tsx`

Expected: FAIL because the create page does not exist.

- [ ] **Step 3: Implement a single Ant Design form**

Use one visible form containing title, category, optional document number, summary, maintainer name (default `陈工`), department, version number (default `V1.0`), change summary (default `首次发布`), and `Upload.Dragger`. Do not split metadata and files into tabs. Show upload progress/error per file, retain successful files when one fails, and allow removal before saving.

- [ ] **Step 4: Implement save and publish commands**

“保存草稿” validates minimum fields, creates the draft once, and then navigates to the draft result without issuing publish. “发布文档” validates, creates the draft if needed, opens a confirmation with document number (`系统自动生成` when empty), version, and file names, then calls publish and navigates to `/documents/{id}` after success. Disable duplicate submits while a mutation runs.

- [ ] **Step 5: Implement dirty-leave protection**

Use React Router's available navigation-blocking API plus `beforeunload`; enable it only after a user changes a field or file queue, and disable it after successful save/publish. The confirmation text is “当前内容尚未保存，确认离开吗？”.

- [ ] **Step 6: Run create workflow tests**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentCreatePage.test.tsx`

Expected: PASS.

## Task 11: Build the Current-Version Detail Workspace

**Files:**
- Create: `frontend/src/pages/main/document-detail/index.tsx`
- Create: `frontend/src/features/documents/components/DocumentPreview.tsx`
- Test: `frontend/src/features/documents/__tests__/DocumentDetailPage.test.tsx`

- [ ] **Step 1: Write failing detail tests**

```tsx
it('shows files, preview, and document information together', async () => {
  renderDetail('/documents/101')
  expect(await screen.findByRole('heading', { name: '焊接工位作业指导书' })).toBeInTheDocument()
  expect(screen.getByText('当前版本 V1.0')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /welding-instruction.pdf/ })).toBeInTheDocument()
  expect(screen.getByTitle('文档预览')).toHaveAttribute('src', expect.stringContaining('preview=true'))
})

it('keeps download available when preview cannot load', async () => {
  renderDetail('/documents/101')
  fireEvent.error(await screen.findByTitle('文档预览'))
  expect(screen.getByText('预览加载失败')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '下载文件' })).toBeInTheDocument()
})
```

- [ ] **Step 2: Run detail tests and verify they fail**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentDetailPage.test.tsx`

Expected: FAIL because the detail workspace does not exist.

- [ ] **Step 3: Implement the three-column desktop workspace**

Use `220px minmax(420px, 1fr) 280px` with responsive constraints down to 1024 px. The left column lists only current-version files, the center is an unframed preview surface, and the right column shows title, number, category, summary, maintainer, department, publication time, and status. Do not add history, relation, comment, favorite, or placeholder cards.

- [ ] **Step 4: Implement preview and download behavior**

PDF uses an `<iframe title="文档预览">`; images use `<img alt="文档预览">` with `object-fit: contain`. Non-previewable files show format, size, SHA-256 prefix, and a download command. A preview error changes only the center panel and preserves file selection, metadata, and download.

- [ ] **Step 5: Run detail tests**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/DocumentDetailPage.test.tsx`

Expected: PASS.

## Task 12: Wire Routing and Navigation

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/app/AppShell.tsx`
- Test: `frontend/src/features/documents/__tests__/documentRouting.test.tsx`

- [ ] **Step 1: Write a failing route/navigation test**

```tsx
it('opens the document workspace from primary navigation', async () => {
  renderApplication('/documents')
  expect(await screen.findByRole('heading', { name: '文档中心' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '文档中心' })).toHaveAttribute('aria-current', 'page')
})
```

- [ ] **Step 2: Run the route test and verify it fails**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__/documentRouting.test.tsx`

Expected: FAIL because no document routes or navigation item exist.

- [ ] **Step 3: Add lazy routes and primary navigation**

```tsx
const DocumentSearchPage = lazy(() => import('./features/documents/DocumentSearchPage'))
const DocumentCreatePage = lazy(() => import('./features/documents/DocumentCreatePage'))
const DocumentDetailPage = lazy(() => import('./pages/main/document-detail'))

<Route path="/documents" element={<DocumentSearchPage />} />
<Route path="/documents/new" element={<DocumentCreatePage />} />
<Route path="/documents/:id" element={<DocumentDetailPage />} />
```

Add `{ key: 'documents', label: '文档中心', path: '/documents', icon: <FileTextOutlined />, active: path => path.startsWith('/documents') }` to `primaryItems`. Apply `aria-current="page"` on active navigation buttons for assistive technology and tests.

- [ ] **Step 4: Run route and all document frontend tests**

Run: `cd frontend && rtk pnpm test -- src/features/documents/__tests__`

Expected: PASS.

## Task 13: Verify, Inspect, and Commit Frontend V1.6.1

**Files:**
- Review: all frontend files from Tasks 7-12

- [ ] **Step 1: Run frontend unit tests**

Run: `cd frontend && rtk pnpm test`

Expected: PASS.

- [ ] **Step 2: Run lint and typecheck**

Run: `cd frontend && rtk pnpm lint`

Expected: PASS.

Run: `cd frontend && rtk pnpm typecheck`

Expected: PASS.

- [ ] **Step 3: Run the production build**

Run: `cd frontend && rtk pnpm build`

Expected: PASS and route-level document chunks emitted.

- [ ] **Step 4: Start both applications for browser verification**

Run backend: `cd backend && rtk mvn spring-boot:run`

Run frontend on an unused port: `cd frontend && rtk pnpm dev -- --host 127.0.0.1 --port 51753`

Expected: backend health responds on `http://127.0.0.1:8080`; frontend responds on `http://127.0.0.1:51753/documents` and proxies `/api` to the backend.

- [ ] **Step 5: Capture browser evidence at both desktop sizes**

At `1366x768` and `1920x1080`, verify:

```text
/documents      category selection, keyword search, row navigation, empty/retry states
/documents/new  visible metadata and upload fields, validation, publish confirmation
/documents/:id  file list, PDF/image preview, metadata, download fallback
```

Expected: no horizontal overflow, overlap, hidden primary form controls, nested cards, unsupported filters, history tabs, relation placeholders, or comments.

- [ ] **Step 6: Review only the frontend diff**

Run: `rtk git diff -- frontend`

Expected: no generated screenshots/build output, no credentials, no hard-coded customer name, and no changes outside V1.6.1.

- [ ] **Step 7: Commit the frontend version**

```bash
git add frontend
git commit -m "feat(前端): 实现文档中心检索工作台" -m "产品版本：V1.6.1"
```

## Task 14: Final Cross-Layer Acceptance

**Files:**
- Review: `docs/specs/2026-07-25-document-center-v1-design.md`
- Review: `docs/requirements/document-center.md`
- Review: relevant committed backend/frontend diffs

- [ ] **Step 1: Verify the repository is clean except for unrelated user work**

Run: `rtk git status --short --branch`

Expected: no uncommitted V1.6.1 files and no generated artifacts.

- [ ] **Step 2: Verify commit separation and product version bodies**

Run: `rtk git log -3 --format=fuller`

Expected: distinct plan, backend, and frontend commits; backend/frontend bodies contain `产品版本：V1.6.1`.

- [ ] **Step 3: Recheck acceptance boundaries**

Confirm the running product supports draft creation, first publication, published-only search, current detail, PDF/image preview, and other-file download. Confirm it does not expose second-version creation, history switching, asset/document relations, favorites, comments, likes, disable actions, approvals, signatures, or online editing.
