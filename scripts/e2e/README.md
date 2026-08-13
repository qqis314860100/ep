# E2E 全流程自动化测试（从 0 到 尾）

在干净的 in-memory（`dev` profile）环境下，从 0 启动后端与前端，mock 一批 PDF / 三维模型 / 附件 / 文档文件上传，驱动完整业务闭环，并逐项断言。

## 快速开始

```bash
bash scripts/e2e/run-e2e.sh
```

脚本会依次：

1. 生成 mock 文件（`scripts/e2e/.mock-files/`，含真实 `%PDF` 签名的最小合法 PDF）
2. 启动后端（`mvn spring-boot:run`，`dev` profile，端口 8080）并等待健康检查
3. 启动前端（`pnpm dev`，端口 5173，`VITE_USE_MOCKS=false` 走真实 API）
4. 执行 `flow.mjs` 全流程脚本
5. 汇总 PASS/FAIL 并关停服务

## 环境变量

| 变量 | 作用 |
|---|---|
| `SKIP_FRONTEND=1` | 跳过前端启动（仅跑后端 API 闭环） |
| `KEEP_SERVERS=1` | 结束后不关停服务，便于人工复核 |

## flow.mjs 覆盖的阶段

- **阶段 0** 健康检查 + 字典基线（ASSET_TYPE / SPECIALTY / DOCUMENT_CATEGORY）
- **阶段 1** mock 文件上传（`POST /uploads/files`，PDF / X_T / TXT / PNG 均校验文件签名）
- **阶段 2** 资产生命周期：建草稿 → 提交 → **待整理**
- **阶段 3** 治理扫描：手动触发 → 运行成功 → 问题池
- **阶段 4** 治理闭环（正式流程）：建任务 → 计划 → 启动（计划锁定）→ 执行（保存草稿 + 提交结果）→ 业务确认 → 质量验收（固定抽样）→ **正式应用** → 任务 COMPLETED、问题 RESOLVED
- **阶段 4b** 自有资产治理闭环：**指派资产责任人** → 扫描自有资产 → 建任务 → 执行（修正适用范围）→ 以责任人身份确认 → 验收 → 正式应用 → 任务 COMPLETED、问题 RESOLVED
- **阶段 5** 知识文档：草稿 → 发布 → 检索
- **阶段 6** 资产文档关联：建立 + 资产侧/文档侧双向查询
- **阶段 7** 收藏 / 评论 / 点赞
- **阶段 8** 文件预览（真实 PDF 内容）+ 打包下载（ZIP）
- **阶段 9** 统一检索：资产与文档同框命中
- **阶段 10** 前端冒烟（可选，需 `--frontend`）

## 已知说明（dev profile）

- 治理闭环的正式应用作业会标记资产标准化，但 **dev profile 的 in-memory 治理适配器维护独立状态映射，不会回写资产仓储**；因此端到端断言以「任务 COMPLETED + 作业 SUCCEEDED + 问题 RESOLVED」为准。
- 新建资产通过 `PUT /api/v1/governance/asset-responsibilities/{assetId}` 指派责任人（需 CONTENT_ADMIN / SYSTEM_ADMIN 角色），即可进入业务确认环节；阶段 4b 完整验证了该能力。
- 治理扫描对问题资产盖章的版本是 `updatedAt` 毫秒时间戳；dev 的内存资产适配器在首次正式应用时以此版本为基线对齐（见 `InMemoryGovernanceAssetAdapter`），使扫描产生的问题可完成闭环。
- 全部生成物（`.mock-files/`、`.logs/`）已在 `.gitignore` 中忽略，不会误提交。

## 手动运行 flow.mjs

后端已启动时可直接：

```bash
node scripts/e2e/flow.mjs --backend http://127.0.0.1:8080 [--frontend http://127.0.0.1:5173]
```

退出码：全部通过 = 0；任一断言失败 = 1（失败会继续跑完并汇总明细）。
