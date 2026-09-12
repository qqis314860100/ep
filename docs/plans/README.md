# docs/plans — 功能 spec 与 tickets（活跃）

开发工作流的本地产物目录（见根目录 `AGENTS.md` 的 "How Work Is Driven"）：

- 每个功能一个文件：`docs/plans/<yyyy-mm-dd>-<slug>.md`。
- 内容 = `to-spec` 产出的 spec（问题、方案、验收标准、范围外），以及
  `to-tickets` 拆出的垂直切片 ticket（阻塞依赖、每票验收条件）。
- 每票带一行 `测试：`，按 **正常 / 边界 / 异常** 三类列出用例；业务规则
  （状态流转、统计口径、`AssetScope` 过滤）的用例由人核验，不采信模型自拟。
- 未配置 issue tracker，因此 tickets 不发布到外部系统；本地文件即事实。
- 完成后如需保留即归档：把文件移入
  `docs/archive/<yyyy-mm-dd>-<里程碑名>/`。

禁止：在仓库根创建 `.scratch/`、一次性清单或进度笔记。
