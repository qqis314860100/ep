# 本地 MySQL 联调

## 1. 初始化数据库

```bash
MYSQL_PWD=你的密码 mysql -h 127.0.0.1 -P 3306 -u root \
  < docs/migrations/local/V1_5__local_bootstrap.sql
MYSQL_PWD=你的密码 mysql -h 127.0.0.1 -P 3306 -u root \
  < docs/migrations/local/V1_5__local_seed.sql
```

两个脚本均可重复执行，不会删除或清空已有数据。

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
