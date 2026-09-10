# 本地 MySQL 联调

> 真实数据库是**唯一**数据源。后端不再有内存/演示数据通路：默认 profile 即 `local`，
> 启动即连库；`src/main` 中不存在任何 `InMemory*` 仓储（它们只作为测试替身留在 `src/test`）。

## 0. 一键补齐库结构（推荐，可重复执行）

归档正本迁移 V1_6..V1_13 面向 OceanBase（`ADD COLUMN IF NOT EXISTS` 等语法），本机 MySQL 9.6 需适配执行。运行仓库内幂等补齐脚本即可把资产/文档/治理缺表、本地 `operation_log_ext`，以及仓库自有的 `scripts/db/migrations/V1_14__system_user_schema.sql`（真实用户/角色/数据范围表 + 引导管理员）一并建好，**不清库、不删数据**：

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

前端从 `frontend/.env.example` 创建 `frontend/.env.local`，只需指向后端：

```dotenv
VITE_API_BASE_URL=
```

（前端已无 mock 数据通路，`VITE_USE_MOCKS` 与 `src/data/mockAssets.ts` 均已删除。）

## 3. 启动后端

默认 profile 就是 `local`，无需显式指定：

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

## 5. 登录账号

用户、角色与数据范围全部来自真实表 `sys_user` / `sys_user_role` / `sys_user_scope`
（迁移 V1_14 建表），不再有任何硬编码演示账号。

清库或首次建库后，脚本会写入唯一的引导管理员（`INSERT ... WHERE NOT EXISTS`，不会覆盖已存在的同名账号）：

| 工号 | 姓名 | 角色 | 初始密码 |
| --- | --- | --- | --- |
| `admin` | 系统管理员 | `SYSTEM_ADMIN` + `CONTENT_ADMIN` | `Admin@2026!` |

**首次登录后请立即改密。** 目前没有改密页面，用下面的 SQL 生成并写入新哈希
（哈希格式与后端 `PasswordHasher` 一致：PBKDF2-HMAC-SHA256，60000 轮，16 字节盐，格式 `base64(salt):iterations:base64(hash)`）：

```bash
python3 -c "
import hashlib, base64, os, sys
pwd = sys.argv[1]
salt = os.urandom(16)
dk = hashlib.pbkdf2_hmac('sha256', pwd.encode(), salt, 60000, dklen=32)
print(base64.b64encode(salt).decode() + ':60000:' + base64.b64encode(dk).decode())
" '你的新密码'
```

```sql
UPDATE sys_user SET password_hash = '<上一步输出>' WHERE code = 'admin';
```

新增真实用户时同样按此流程插入 `sys_user` / `sys_user_role` / `sys_user_scope` 三张表。

