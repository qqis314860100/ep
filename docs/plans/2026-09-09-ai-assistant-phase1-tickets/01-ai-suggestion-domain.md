# 01: AI 建议-确认域闭环

**What to build:** 内容管理员能在 ep 后端对一条「AI 建议」做全生命周期管理：建议以待确认状态落库（携带目标资产/文档、目标自身范围、建议内容、依据与置信度、来源=AI），可对待确认建议确认或驳回；确认后走现有资产写入链路生效并写操作日志；重复整理使同目标的旧待确认建议作废；任何查询与操作都只作用于当前用户 AssetScope 内的目标。本票是建议域与信任边界的核心，不依赖真实 AI 能力服务（建议来源先用测试数据/种子/内置 fake 造入）。

**Blocked by:** None（可立即开工）

**Status:** ready-for-agent

- [ ] AI 建议实体按 spec 状态机落库：`PENDING → CONFIRMED | REJECTED`；同目标新建议进入 PENDING 时旧 PENDING 置为 `SUPERSEDED`
- [ ] 建议载荷按 spec 契约存储：targetType/targetId/targetScope、proposed（命名/分类/标签/摘要/范围提示，仅资产支持字段）、evidence 片段、confidence、source="AI"
- [ ] 清单 API：按状态（待确认/已确认/已驳回）与目标筛选、分页；只返回当前用户 AssetScope 内目标的建议
- [ ] 确认 API：调用现有资产写入/文档发布链路的方法完成生效（不得旁路写库）；随后写操作日志（含 AI 来源与依据快照）
- [ ] 驳回 API：置 REJECTED，不产生业务变更
- [ ] 越权：范围外目标的建议不可见、不可确认/驳回（API 层测试覆盖 403/过滤）
- [ ] 仓储沿袭现有先例：默认 dev 用内存仓储，local 用 JDBC 仓储；schema 支持上述字段
- [ ] 验收测试在主缝（API 层，fake 数据源）收口：状态机流转、越权、确认后资产动作与审计；application 层补状态机与越权单测

对应 spec 用户故事：15–22、23–25、28–29、31–32。
