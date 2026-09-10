# 真实数据库唯一数据源（去 mock / 去内存后端）

> 日期：2026-09-10　状态：进行中　关联：`docs/plans/2026-09-08-real-data-office-upload-formats.md`（上一轮：保留 dev 内存 profile，仅提供 local 可选启动）

## 1. 用户诉求（原话）

「前后端需要接入真实数据库，我不需要 mock 数据了。」

即：真实数据库成为**唯一**数据源，内存/mock 通路整体拆除，而不是再加一个开关。

## 2. 现状（已核实，2026-09-10）

| 项 | 现状 |
| --- | --- |
| 后端默认 profile | `dev`：`application-dev.yml` 直接 `exclude DataSourceAutoConfiguration`，资产/文档/字典/协作/文件/治理/AI 全部走 `InMemory*` 仓储，重启即丢；`asset.governance-schema-enabled=false` 时由 `GovernanceStoreConfiguration` 供给内存种子（`withLegacySeed()` / `withFieldSeeds()`）。 |
| 真实库通路 | 已实现但只在 `local` / `oceanbase` profile 生效：`OceanBaseAssetRepository`（读遗留 `sys_drawing`）、`JdbcDocumentRepository`、`JdbcGovernance*Store`、`JdbcDictionaryStore` 等。本机 MySQL 9.6 `tianshu` 在线。 |
| 后端残留硬编码身份 | `InMemorySystemUserRepository`（`@Profile({"dev","local"})`）写死 4 个演示账号（emp-chen/emp-li/emp-wang/emp-admin，密码 `demo123`）；多个 controller 的 `X-User-Id` 默认值为 `demo-user`。 |
| 前端 | `src/data/mockAssets.ts` 整套假资产；`assetService.ts` 中 `useMocks = VITE_USE_MOCKS !== 'false'`——**环境变量缺失时默认开启 mock**；登录页写死演示账号、上传页写死 `ownerName: '陈工'`、评论写死 `authorName`。 |

## 3. 决策记录（2026-09-10 用户确认）

- **D1 目标库 = 本机 MySQL 3306 / `tianshu`**；`oceanbase` profile 作为生产部署形态保留。
- **D2 内存实现处置 = 从生产代码删除**：`InMemory*` 全部移出 `src/main`，作为测试替身留在 `src/test`；测试改为直接构造替身或使用 H2。
- **D3 登录身份落库 = 建真实用户表**（用户 + 角色 + 数据范围 + 密码凭证），前端去掉演示账号与预填密码。
- **D4 清库范围 = 只清应用表与演示种子**，保留 `sys_drawing` / `sys_file` / `temp_person` / `dictionary_item`（遵循 AGENTS.md「不改遗留主键/来源值」）；保留 1 个引导管理员账号，否则清库后无人能登录、无人能导入真实数据。
- **D5 执行前必须先 mysqldump 备份**到 `/tmp`（不入库、不提交）。

## 4. 非目标

- 不连接、不修改任何生产库；不改遗留表主键与来源值。
- 不改 `sys_drawing` 等遗留表的表结构。
- 不引入外部 IdP；密码仍为 PBKDF2-HMAC-SHA256（`PasswordHasher`），生产建议后续升级 BCrypt/Argon2。

## 5. 验收标准

1. `backend/src/main` 中不存在任何 `InMemory*` 数据实现，也不存在 `dev` profile 与内存种子。
2. 不设置任何 profile 启动后端即连接真实 MySQL（`spring.profiles.default=local`），资产/文档/字典/治理读接口返回库内数据，不再返回内存种子。
3. 登录、评论作者、上传者均来自库内用户；前端无演示账号、无预填密码、无 mock 数据文件。
4. 库里应用表演示种子已清理，`sys_drawing` / `sys_file` / `temp_person` / `dictionary_item` 数据未被改动；存在 1 个引导管理员可登录。
5. `rtk mvn test`、`rtk pnpm lint`、`rtk pnpm typecheck`、`rtk pnpm test` 全绿。

## 6. 任务拆解

- **T1 前端去 mock**：删除 `src/data/mockAssets.ts` 与 `assetService.ts` 的 `useMocks` 双通路；登录页去演示账号；上传者/评论作者取自会话；删除 `VITE_USE_MOCKS`。
- **T2 后端去内存**：默认 profile 改 `local`；删除 `application-dev.yml`；`InMemory*` 移入 `src/test`；`GovernanceStoreConfiguration` 只保留非内存装配；`SessionIdentityFilter` 常驻。
- **T3 用户落库**：新增 `sys_user` / `sys_user_role` / `sys_user_scope` 表与 `JdbcSystemUserRepository`；引导管理员；去掉 `X-User-Id` 的 `demo-user` 默认值。
- **T4 迁移脚本**：新表 DDL 落到 `scripts/db/`，接入 `prepare_local_db.sh` 的幂等执行与 `schema_migration_applied` 台账。
- **T5 清库**：备份 → 清应用表 → 写引导管理员。
- **T6 验证与提交**：后端全量测试 + local 启动冒烟；前端三项检查；后端/前端分开提交。
