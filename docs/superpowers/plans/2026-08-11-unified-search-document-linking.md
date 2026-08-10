# 统一检索、文档适用范围与双向关联 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use task-by-task execution with the checkboxes below. Steps are intentionally small and each completed layer is committed independently.

**Goal:** 交付 V1.7.0 的统一检索、文档适用范围、资产文档双向关联与审计闭环。

**Architecture:** 保持资产与知识文档为独立领域。新增文档范围和资产文档关系端口，统一检索应用服务只编排两个查询端口并返回独立结果区。标准设备模块继续是设置 `standardEquipmentModule` 标志的资产，不创建多态关联或独立模块主表。

**Tech Stack:** Java 21、Spring Boot 3、Spring JDBC、OceanBase MySQL 兼容 SQL；React 18、TypeScript、TanStack Query、Ant Design 5、styled-components。

---

## 文件结构

- `docs/migrations/V1_11__document_scope_and_relation.sql`：正式增量结构。
- `docs/migrations/local/V1_11__local_document_scope_and_relation.sql`：本地幂等结构。
- `backend/.../document/domain/DocumentScopeMode.java`、`DocumentScope.java`：文档范围值对象。
- `backend/.../documentrelation/`：资产文档关系领域、应用、API 与内存/JDBC 适配器。
- `backend/.../search/`：只读统一检索编排与 API。
- `frontend/src/types/document.ts`、`unifiedSearch.ts`：稳定的前端契约。
- `frontend/src/features/assets/AssetSearchPage.tsx`：保留 AppShell 的统一检索结果区。
- `frontend/src/features/documents/components/DocumentForm.tsx`：文档范围和关联编辑。
- `frontend/src/pages/main/detail/index.tsx`、`document-detail/index.tsx`：双方详情关联区。

### Task 1: 更新需求基线和迁移契约

**Files:**

- Modify: `docs/superpowers/specs/2026-07-26-unified-search-document-linking-design.md`
- Modify: `docs/requirements/document-center.md`
- Modify: `docs/requirements/implementation-baseline.md`
- Create: `docs/migrations/V1_11__document_scope_and_relation.sql`
- Create: `docs/migrations/local/V1_11__local_document_scope_and_relation.sql`

- [ ] 将设计中“V1.7 增量迁移”明确为“产品 V1.7.0 使用数据库 V1_11”，不改动已有 V1_7 至 V1_10 治理迁移。
- [ ] 将文档范围规则更新为新建/编辑必须显式 `GLOBAL` 或 `SPECIFIED`，存量文档为 `UNCLASSIFIED`。
- [ ] 编写失败性 DDL 审查：确认 SQL 仅含 `ALTER TABLE ... ADD COLUMN` 与 `CREATE TABLE IF NOT EXISTS`，不含 `DROP`、`DELETE`、旧表主键改写或旧字段回填。
- [ ] 编写并检查迁移：

```sql
ALTER TABLE knowledge_document
  ADD COLUMN scope_mode VARCHAR(32) NOT NULL DEFAULT 'UNCLASSIFIED';

CREATE TABLE IF NOT EXISTS document_scope (... document_id BIGINT NOT NULL ...);
CREATE TABLE IF NOT EXISTS asset_document_relation (... drawing_id BIGINT NOT NULL, document_id BIGINT NOT NULL ...);
CREATE TABLE IF NOT EXISTS asset_document_relation_audit (... relation_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL ...);
```

- [ ] 提交文档与迁移：`docs(需求): 固化文档范围与关联迁移契约`，正文为 `产品版本：V1.7.0`。

### Task 2: 文档范围领域与仓储契约

**Files:**

- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentScopeMode.java`
- Create: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentScope.java`
- Modify: `backend/src/main/java/com/tianshu/assets/document/domain/KnowledgeDocument.java`
- Modify: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentSearchCriteria.java`
- Modify: `backend/src/main/java/com/tianshu/assets/document/domain/DocumentRepository.java`
- Modify: `backend/src/main/java/com/tianshu/assets/document/application/CreateDocumentDraftCommand.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/application/DocumentCommandServiceTest.java`

- [ ] 先增加失败测试：草稿没有 `scopeMode`、`GLOBAL` 携带范围、`SPECIFIED` 没有范围均失败；`UNCLASSIFIED` 只能由迁移数据读取，不能由新建命令提交。
- [ ] 增加不可变类型：

```java
public enum DocumentScopeMode { GLOBAL, SPECIFIED, UNCLASSIFIED }
public record DocumentScope(long id, long documentId, String platformFamily, String platformVariant,
    String productLine, String baseName, String productionLine, String processSection) {}
```

- [ ] 扩展 `KnowledgeDocument` 和创建命令，保存 `scopeMode`、`List<DocumentScope>`；空值标准化为不可变空列表。
- [ ] 将 `DocumentSearchCriteria` 扩展为关键词、分类、六个范围维度和分页；新增 `hasScopeFilter()`。
- [ ] 在命令服务中用同一方法校验范围模式，保持首次发布和文件校验不变。
- [ ] 运行 `rtk mvn -Dtest=DocumentCommandServiceTest test`，通过后继续。

### Task 3: 文档范围内存与 JDBC 查询

**Files:**

- Modify: `backend/src/main/java/com/tianshu/assets/document/infrastructure/InMemoryDocumentRepository.java`
- Modify: `backend/src/main/java/com/tianshu/assets/document/infrastructure/JdbcDocumentRepository.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/infrastructure/InMemoryDocumentRepositoryTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/document/infrastructure/JdbcDocumentRepositoryTest.java`

- [ ] 先写失败测试：带基地和拉线时，文档必须由同一条 `DocumentScope` 同时命中；`GLOBAL` 始终命中；`UNCLASSIFIED` 在有范围筛选时不命中；多范围命中时文档只返回一次。
- [ ] 内存仓储为两份已发布演示文档提供一个 `SPECIFIED` 和一个 `GLOBAL` 范围，迁移演示文档保持 `UNCLASSIFIED`。
- [ ] JDBC 查询使用 `EXISTS (SELECT 1 FROM document_scope ...)` 表达所有指定范围条件，不能为每个条件创建独立 `EXISTS`。
- [ ] 保存/更新文档时原子替换 `document_scope` 行；JDBC 事务失败不得留下部分范围。
- [ ] 运行两个仓储测试类，确认两种仓储的分页和范围行为一致。

### Task 4: 资产文档关系领域、审计与 API

**Files:**

- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/domain/AssetDocumentRelation.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/domain/AssetDocumentRelationType.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/domain/AssetDocumentRelationRepository.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/application/AssetDocumentRelationService.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/infrastructure/InMemoryAssetDocumentRelationRepository.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/infrastructure/JdbcAssetDocumentRelationRepository.java`
- Create: `backend/src/main/java/com/tianshu/assets/documentrelation/api/AssetDocumentRelationController.java`
- Modify: `backend/src/main/java/com/tianshu/assets/asset/api/AssetController.java`
- Test: `backend/src/test/java/com/tianshu/assets/documentrelation/application/AssetDocumentRelationServiceTest.java`
- Test: `backend/src/test/java/com/tianshu/assets/documentrelation/api/AssetDocumentRelationControllerTest.java`

- [ ] 先写失败测试：允许关联普通资产和 `standardEquipmentModule` 资产；同一资产、文档、类型不能重复；改类、解除和恢复均追加审计；解除不删除资产或文档。
- [ ] 建立三种固定枚举 `COMPANION`、`APPLICABLE`、`REFERENCE`，不接受自由字符串。
- [ ] 关系服务在新增或恢复前验证资产存在、文档存在且不是停用状态；写入关系和审计使用同一事务。
- [ ] 提供如下 HTTP 契约：

```text
GET    /api/v1/documents/{id}/asset-relations
GET    /api/v1/assets/{id}/documents
POST   /api/v1/asset-document-relations
PATCH  /api/v1/asset-document-relations/{id}
DELETE /api/v1/asset-document-relations/{id}
```

- [ ] 控制器从 `X-User-Id`、`X-User-Name` 记录操作人；缺失对象和无权对象采用不泄露信息的 `404`，重复和并发冲突返回 `409`。
- [ ] 运行关系应用服务和控制器测试，再运行 `rtk mvn -Dtest=DocumentControllerTest,AssetControllerTest test`。

### Task 5: 后端统一检索 API

**Files:**

- Create: `backend/src/main/java/com/tianshu/assets/search/application/UnifiedSearchService.java`
- Create: `backend/src/main/java/com/tianshu/assets/search/api/UnifiedSearchController.java`
- Create: `backend/src/main/java/com/tianshu/assets/search/api/UnifiedSearchResponse.java`
- Modify: `backend/src/main/java/com/tianshu/assets/asset/domain/AssetSearchCriteria.java`
- Modify: `backend/src/main/java/com/tianshu/assets/asset/api/AssetResponse.java`
- Test: `backend/src/test/java/com/tianshu/assets/search/api/UnifiedSearchControllerTest.java`

- [ ] 先写失败测试：`GET /api/v1/search` 同时返回 `assets` 和 `documents`；资产范围与文档范围均在同一个范围对象内匹配；两个分页参数互不影响。
- [ ] 将蓝本和工序段加入资产检索条件，保持所有范围字段从一个 `AssetScope` 命中。
- [ ] 统一服务只调用 `AssetQueryService` 与 `DocumentQueryService`，响应结构为：

```java
record UnifiedSearchResponse(Section<AssetResponse> assets, Section<DocumentResponse> documents) {}
record Section<T>(List<T> data, PageMeta meta, String status, String errorCode) {}
```

- [ ] 保留 `/api/v1/assets` 和 `/api/v1/documents` 原有契约，不让统一检索替换文档中心维护检索。
- [ ] 运行新增控制器测试和完整 `rtk mvn test`；通过后提交后端：`feat(后端): 实现统一检索与文档关联`，正文为 `产品版本：V1.7.0`。

### Task 6: 前端类型、服务和文档维护表单

**Files:**

- Modify: `frontend/src/types/document.ts`
- Modify: `frontend/src/services/documentService.ts`
- Create: `frontend/src/services/unifiedSearchService.ts`
- Modify: `frontend/src/features/documents/components/DocumentForm.tsx`
- Modify: `frontend/src/features/documents/DocumentCreatePage.tsx`
- Test: `frontend/src/features/documents/__tests__/documentService.test.ts`
- Test: `frontend/src/features/documents/__tests__/DocumentCreatePage.test.tsx`

- [ ] 先写失败测试：创建请求序列化 `scopeMode` 与完整 `scopes`；`SPECIFIED` 无范围时阻止保存；选中 `GLOBAL` 时范围行不进入请求。
- [ ] 为前端 `KnowledgeDocument` 增加 `scopeMode`、`scopes`，为 `AssetDocumentRelation` 增加稳定 ID、资产摘要、文档当前版本和关系类型。
- [ ] 文档表单使用 Ant Design `Radio.Group` 管理“全局通用/指定范围”，指定范围复用字典选择器添加完整行；不使用嵌套卡片或页签。
- [ ] 增加关联资产检索与关系类型选择，选择项用资产编号、名称和“标准设备模块”标志区分。
- [ ] 运行 `rtk pnpm test -- DocumentCreatePage documentService`，测试通过后继续。

### Task 7: 统一检索与双方详情体验

**Files:**

- Modify: `frontend/src/features/assets/AssetSearchPage.tsx`
- Modify: `frontend/src/pages/main/search/components/SearchSidebar.tsx`
- Create: `frontend/src/features/documents/components/DocumentSearchResultSection.tsx`
- Modify: `frontend/src/pages/main/detail/index.tsx`
- Modify: `frontend/src/pages/main/document-detail/index.tsx`
- Modify: `frontend/src/services/assetService.ts`
- Test: `frontend/src/features/assets/__tests__/AssetSearchPage.test.tsx`
- Test: `frontend/src/features/documents/__tests__/DocumentDetailPage.test.tsx`

- [ ] 先写失败测试：统一检索同页显示资产区和文档区；一方无结果或失败时另一方仍可用；生产条件传入两个结果区。
- [ ] 保留现有 AppShell、目录和资产画廊/列表切换。资产区继续使用现有结果组件；文档区使用紧凑行列表而不是大型卡片或 Tab。
- [ ] 在资产详情首屏“关联资料”中加入按关系类型分组的文档；在文档详情固定右侧信息栏加入关联资产，不放在评论之后。
- [ ] 有维护权限时在双方详情提供新增、改类、解除命令；普通用户只读。操作成功后失效双方查询缓存。
- [ ] 运行 `rtk pnpm test -- AssetSearchPage DocumentDetailPage`、`rtk pnpm lint`、`rtk pnpm typecheck` 和 `rtk pnpm build`。
- [ ] 启动前端并在 `1366x768`、`1920x1080` 验证统一检索、文档创建范围、双向跳转、关联维护和无横向溢出。
- [ ] 提交前端：`feat(前端): 实现统一检索与文档关联`，正文为 `产品版本：V1.7.0`。

### Task 8: 收口文档、回归与交付

**Files:**

- Modify: `docs/requirements/implementation-baseline.md`
- Modify: `docs/technical-design.md`
- Modify: `requirement.md`

- [ ] 将实施基线中的文档中心状态改为实际已实现能力，并将 V1.7.0 标记为已实现，不把后续文档版本、评论或停用伪报为完成。
- [ ] 将技术设计 API 与 V1_11 迁移编号同步；保持“蓝本”用户术语和 `productLine` 兼容字段的说明。
- [ ] 运行 `rtk git diff --check`、`rtk mvn test`、`rtk pnpm lint`、`rtk pnpm typecheck`、`rtk pnpm build`。
- [ ] 审查只暂存本版本文档文件并提交：`docs(需求): 更新统一检索实施基线`，正文为 `产品版本：V1.7.0`。
