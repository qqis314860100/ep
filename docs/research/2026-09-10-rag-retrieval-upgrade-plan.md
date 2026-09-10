# RAG 检索层升级计划（embedding 选型 + 混合检索 + 向量库替换 + 评测门禁）

> 日期：2026-09-10　性质：research / 跨仓执行前设计（ep 侧勘察结论 + 目标契约与执行顺序；本文件不提交 ai-rag 代码）
> 关联：`docs/research/2026-09-09-ai-rag-generalization-plan.md`（契约泛化，ai-rag 侧已落地 namespace/scopes/X-Service-Key//extract/fileContentBase64）；`docs/plans/2026-09-09-ai-agent-platform-architecture.md`（L3 能力服务定位）
> 共识前提（2026-09-10 会话确认）：RAG 能力服务**保持 Python**（不迁 TS）；embedding 走平台托管小模型（形态 B，本地不内嵌权重）；「TS 主流」作用于应用层，不作用于 L3 能力服务。
> 执行状态（2026-09-10 更新）：**P0 / P1 / P2 / P2.5 已在 ai-rag 仓执行完成**（评测先行，全部以 golden diff 决策）；**P3 暂缓**（见 §0）。

## 0. 执行状态与结论（2026-09-10 更新，ai-rag 仓）

**提交（ai-rag 仓，独立治理）**：`09f369f` P0 基线评测 → `3ee33a4` P1 embedding A/B 支持 → `d7834a4` P2 混合候选生成 → `f505261` P2.5 cross-encoder 重排。

**评测口径**：同一 golden 36 条（14 篇语料全覆盖 + 跨文档）、top_k=10、fresh 独立索引（`CHROMA_PERSIST_DIR` 隔离，不碰现网索引）。

| 配置 | hit@5 | recall@5 | mrr@10 | ndcg@10 |
| --- | --- | --- | --- | --- |
| 现网旧索引（legacy bge） | 1.0000 | 0.9861 | 0.8736 | 0.8904 |
| fresh bge，仅向量候选（P0 基线） | 0.9722 | 0.9583 | 0.8542 | 0.8694 |
| fresh bge + 混合候选（**P2，默认开**） | 1.0000 | 0.9861 | 0.8574 | 0.8795 |
| fresh bge + 混合候选 + cross 重排（**P2.5，可选**） | 1.0000 | 0.9861 | **0.8667** | **0.8873** |
| fresh Qwen3-Embedding-0.6B（P1 A/B） | 0.9444 | 0.9306 | 0.8604 | 0.8756 |

**结论**：

1. **P1：embedding 不切换**。Qwen3-Embedding-0.6B 在本语料未胜出（hit/recall 反降），瓶颈是文档级主题区分度已触顶 + 跨主题召回，不是 embedding 表达力 → 保持 `bge-small-zh-v1.5`。若将来 ep-docs 语料上量、语义型查询占比升高，再复测（届时可试 4B/8B 与 query 指令前缀）。
2. **P2：混合候选生成默认开启**（`RAG_HYBRID_CANDIDATES=true`）：向量 rerank 结果权威，词法路只把"向量未召回的新文档"限量（≤3 个）经 RRF 插队；跨主题缺口 b-015/b-017/b-036 全部回收，全指标净提升。
3. **P2.5：cross 重排可选**（`RAG_RERANK_MODE=cross`，默认仍 `local`）：全指标最优，但 CPU 每查询多约秒级延迟；是否默认开由部署延迟预算决定（内网单机、问答场景可开）。
4. **P3：向量库迁移暂缓**。当前语料量级下自研词法扫描（全量 BM25）已足够，Qdrant 原生 sparse 的收益要等语料上万级或需要独立检索服务时才成立；届时以本 golden 作迁移验收门禁。

**过程中值得记住的负结果（避免重复踩坑）**：

- BM25 分数按语料归一化后 top 恒≈1.0，若直接进候选池会被 rerank 误当作"强向量证据"挤掉真实语义命中 → 词法命中必须 `score=0`、只补新文档。
- RRF 融合池若先合再进 rerank，重排会冲掉融合序（指标反降）→ 必须"先 rerank 出向量序，再 RRF 插队"。
- cross-encoder 对**全候选池**精排会误伤中段命中；只精排 local 前 20 名、其后保序才拿到增益。
- legacy 索引（旧 schema）与 fresh 重灌（带 section 元数据）存在检索漂移（b-015/b-036 跨主题查询下降）→ 任何重灌/迁移都必须先跑 golden diff，不能默认"重灌无损"。

**仍未闭环**：ep-docs 语料 golden（待 ep 侧语料入 namespace）；平台 embedding 托管形态确认（仅当将来决定切换 embedding 时才需要）。

## 1. 现状基线（勘察 2026-09-10）

| 项 | 现状（ai-rag 仓事实） |
| --- | --- |
| 栈 | Python FastAPI + ChromaDB + sentence-transformers `bge-small-zh-v1.5`（本地内嵌推理）+ DeepSeek/OpenAI 兼容 + pypdf/python-docx/markdown-it；约 1.4 万行含测试/评测 |
| 管道 | rewrite → 检索（top_k、keyword/LLM rerank 可切换）→ generate → refusal / evidence verification / confidence → usage_guard；`/rag/chat/stream` SSE |
| 泛化状态 | namespace / scopes / X-Service-Key / `/extract` / fileContentBase64 已按 ep 契约落地 → **升级不得回退契约面** |
| 语料 | `knowledge/` 14 篇（电池）；ep-docs namespace 尚未灌语料 |
| 评测 | `rag/evals` 薄（flowchart/knowledge_gap 类样本），**无检索 golden、无回归门禁** |
| 短板 | ① embedding 为 2023 级小模型；② ChromaDB 无原生稀疏/BM25 → 无混合检索；③ 换 embedding/向量库/检索均无评测可证 |

## 2. 升级目标

- **评测先行**：建三层评测（检索 golden / 端到端 / 拒答负例），一切更换先跑 diff，把「现状」先变成可复现数字。
- **Embedding**：`bge-small-zh-v1.5` → **Qwen3-Embedding（0.6B 起步，A/B 决定是否试 4B/8B）**；形态 B = 平台托管推理服务暴露 OpenAI 兼容 embedding 端点（平台无现成端点则自起 vLLM 托管服务）。
- **混合检索**：稠密 + 稀疏（BM25 / 模型 sparse）+ RRF 融合 + reranker 消融（候选 `bge-reranker` cross-encoder）。
- **向量库**：ChromaDB → **Qdrant**（首选：原生 dense+sparse、Python 客户端成熟）或 **LanceDB**（备选：嵌入式、零运维）；评测全绿后迁移，保留 namespace/scopes 语义。
- **契约铁律**：`/rag/*` 端点、SSE 事件序、`X-Service-Key`、namespace/scopes、ingest/extract schema **全部不变**；升级只发生在内部实现。

## 3. 评测方案（P0 交付物，先行落地）

**golden 集结构**（JSONL，置于 `rag/evals/`）：每条 `{ id, query, namespace?, scope?, expected: [{docId/assetId, anchor 片段或段号}], negative?: bool }`；起步 50–100 条，覆盖难样本：术语别名、跨文档、否定式提问、越权试探。

- 检索层指标：`Recall@k (k=5,10)`、`MRR`、`nDCG@k`；消融：仅向量 vs 混合(RRF) vs +reranker。
- 端到端：faithfulness / answer relevance（LLM judge **本地化**，走内网模型）；「引用必须命中检索结果」用**规则判定**，不依赖 judge。
- 拒答负例：范围外 / 无证据问题必须拒答或低置信降级（守 AssetScope 边界，比召回率更优先）。
- 语料：P0 用现有 14 篇电池语料建基线（评测逻辑与 namespace 无关）；ep-docs namespace 就绪后另补一套 golden。
- 运行形态：Python harness、命令行可跑、输出 JSON diff 报告；**评测与实现解耦**——将来即使实现换语言，评测可黑盒复用（HTTP 打服务）。

## 4. 执行顺序（Phase，按顺序在 ai-rag 仓执行）

| Phase | 内容 | 出口标准 | 状态 |
| --- | --- | --- | --- |
| **P0 基线评测** | 建 golden + harness，用当前 bge-small + Chroma 跑出 baseline | golden diff 报告可用；rag/tests + 契约自测全绿 | ✅ 已交付（`09f369f`） |
| **P1 Embedding A/B** | Qwen3-0.6B（托管端点或临时本地）vs baseline，同 golden | diff 量化提升 → 决定切换与档位（是否试 4B/8B） | ✅ 已执行：未胜出，**不切换**（`3ee33a4`） |
| **P2 混合检索** | 在胜出 embedding 上加稀疏路 + RRF + reranker 消融（可先 Chroma+外接 FTS 验证增益） | 混合 vs 单路 diff；reranker 取舍有据 | ✅ 已交付（`d7834a4` + `f505261`） |
| **P3 向量库迁移** | Chroma → Qdrant（或 LanceDB），namespace→collection 映射保留，重灌 | P0 golden diff + 契约测试全绿，电池默认行为不变 | ⏸ 暂缓（触发条件见 §0.4） |
| **P4 门禁化（可选）** | rag/evals 接入 CI/回归脚本；prompt/检索/embedding/向量库改动必跑 | 门禁脚本入库 | ⏳ 待排（harness 已可直接进 CI） |

## 5. 风险与开放项

- **平台侧 embedding 形态确认（已降级为"将来切换时才需要"）**：P1 已用本地临时加载完成 A/B 并判定**不切换**，因此托管形态（A 现成 API / B 自起托管）、模型名与版本固定、配额与内网可达**不再阻塞任何 Phase**；仅当未来决定切换 embedding 时按 A/B 结果重新确认。
- cross 重排的延迟预算：CPU 每查询约多秒级（bge-reranker-base、精排 20 条），默认 `local`；若要默认开，需先确认问答延迟可接受或采用分级策略。
- 切分策略随 embedding 换代可能需重调（原 P1 项；本次未换代，留待未来切换时验证）。
- Qwen3-Embedding 原生支持 dense+sparse：未采用 Qwen3，当前稀疏路由自研 BM25 承担；将来若换 Qwen3 或迁 Qdrant，稀疏路可切原生 sparse。
- 电池 UI 与默认 namespace 行为回归（沿用泛化回归纪律）；**重灌/迁移必须先跑 golden diff**（legacy→fresh 已观测到漂移，见 §0）。
- P3 迁移窗口策略（双写/切换）在迁移前单独定。

## 6. 执行前提与收尾

- 跨仓写入需授权（本会话工作区仅 ep）；改动在 ai-rag 仓按其 EXECUTION_RULES/提交规范独立提交（本轮提交见 §0）。
- 本文件不替代 ai-rag 仓的 PRD/设计；作为两仓检索升级的**契约与顺序基准**。
- ep 侧不受影响：本升级不改变 ep `HttpAiCapabilityClient` 契约；ai-rag 各 Phase 完成后 ep 仅需重灌/冒烟即可联调（`/rag/*` 端点、SSE 事件序、namespace/scopes 全部未变）。
- 复现命令（ai-rag 仓 `rag/` 目录）：
  - 评测：`./.venv/bin/python -m evals.retrieval_eval --top-k 10`
  - cross 模式：环境变量 `RAG_RERANK_MODE=cross`
  - 独立重灌：`CHROMA_PERSIST_DIR=<dir> EMBEDDING_MODEL=<model> ./.venv/bin/python -m evals.ingest_corpus --knowledge-dir ../knowledge`
