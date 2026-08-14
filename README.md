# 仿真数模资产管理系统

面向部门内部的数模资产存储、治理、检索和轻量协作平台。

## 项目结构

- `frontend/`：React、Vite、Ant Design 5、styled-components。
- `backend/`：Java 21、Spring Boot、OceanBase MySQL 兼容模式。
- `requirement.md`：对外产品需求文档源文件（最新基线 V1.8）。
- `仿真数模资产管理系统_产品需求文档_V1.8.docx`：由 `scripts/build_requirement_docx.py` 从 `requirement.md` 生成的对外需求基线；旧版本快照（V1.3/V1.4/V1.5）已删除，git 历史可恢复。
- `docs/technical-design.md`：内部技术设计和开发约束。

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
