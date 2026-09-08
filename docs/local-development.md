# 本地 MySQL 联调

## 0. 一键补齐库结构（推荐，可重复执行）

归档正本迁移 V1_6..V1_13 面向 OceanBase（`ADD COLUMN IF NOT EXISTS` 等语法），本机 MySQL 9.6 需适配执行。运行仓库内幂等补齐脚本即可把资产/文档/治理缺表与本地 `operation_log_ext` 一并建好，**不清库、不删数据**：

```bash
bash scripts/db/prepare_local_db.sh   # 凭据读取仓库根 .env.local
```

## 1. 初始化数据库（首次建空库 / 重灌基础表时）

```bash
MIG=docs/archive/2026-09-07-doc-driven-development/migrations/local
MYSQL_PWD=你的密码 mysql -h 127.0.0.1 -P 3306 -u root \
  < $MIG/V1_5__local_bootstrap.sql
MYSQL_PWD=你的密码 mysql -h 127.0.0.1 -P 3306 -u root \
  < $MIG/V1_5__local_seed.sql
```

两个脚本按上述顺序执行，不会删除或清空已有数据。治理闭环与文档相关表不再直接执行 V1_7 之后的 launcher（已归档且仅 OceanBase 语法），统一用 §0 补齐脚本。执行后应先完成只读核对，再在非生产环境开启治理结构读取。

## 2. 配置环境变量

从仓库根目录的 `.env.example` 创建 `.env.local`，填写本机数据库凭据。
该文件已被 Git 忽略，不得提交。

前端从 `frontend/.env.example` 创建 `frontend/.env.local`，并保持：

```dotenv
VITE_USE_MOCKS=false
```

## 3. 启动后端

```bash
cd backend
set -a
source ../.env.local
set +a
mvn spring-boot:run
```

健康检查：`http://127.0.0.1:8080/actuator/health`。

## 4. 启动前端

```bash
cd frontend
pnpm dev --host 127.0.0.1
```

访问 `http://127.0.0.1:5173/`。
