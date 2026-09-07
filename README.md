# 仿真数模资产管理系统

面向部门内部的数模资产存储、治理、检索和轻量协作平台。

## 项目结构

- `frontend/`：React、Vite、Ant Design 5、styled-components。
- `backend/`：Java 21、Spring Boot、OceanBase MySQL 兼容模式。
- `docs/plans/`：功能 spec 与拆分 ticket（Matt Pocock 技能工作流的本地产物）。
- `docs/archive/2026-09-07-doc-driven-development/`：已归档的需求/技术设计/ADR/迁移等文档驱动线，仅供历史参考。
- `~/.dsh/skills/`：开发工作流技能（Matt Pocock 技能集，MIT；流程见 `AGENTS.md`）。

## 本地启动

前端：

```bash
cd frontend
pnpm install
pnpm dev
```

后端：

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn spring-boot:run
```

默认后端使用内存演示数据，不连接数据库。启用 OceanBase 前，应配置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 并激活 `oceanbase` profile。
