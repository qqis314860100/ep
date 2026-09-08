#!/usr/bin/env bash
# 本机 MySQL（tianshu）补齐脚本：把归档正本迁移 V1_6..V1_13 中缺失的对象补到本地库。
#
# 设计要点
# - 只做 additive：CREATE TABLE IF NOT EXISTS、INSERT IGNORE、按列存在性守卫的 ADD COLUMN。
# - 不删除/不清空任何已有数据；不改写 docs/archive 下的归档正本（本脚本只读取它们）。
# - 幂等可重跑：已应用的文件记录在 tianshu.schema_migration_applied，重复执行自动跳过；
#   若某文件之前半应用（例如失败于中途），重跑时已存在的 ADD COLUMN 会被自动跳过。
# - MySQL-9 兼容：正本面向 OceanBase，使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS 与
#   字符串内 \" 转义；本机 MySQL 9.6 两者都不兼容。本脚本在语句级做适配：
#   * ALTER 的 ADD COLUMN 先查 information_schema，列已存在则该语句整体跳过；
#   * 去除 ADD COLUMN IF NOT EXISTS 子句；
#   * INSERT 的 JSON 字符串去掉多余的 \" 转义。
#
# 用法：bash scripts/db/prepare_local_db.sh
# 凭据：优先读取仓库根 .env.local（DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD），可被环境变量覆盖。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MIG_DIR="${REPO_ROOT}/docs/archive/2026-09-07-doc-driven-development/migrations"

if [[ -f "${REPO_ROOT}/.env.local" ]]; then
  # shellcheck disable=SC1091
  set -a && source "${REPO_ROOT}/.env.local" && set +a
fi

export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-tianshu}"
export DB_USERNAME="${DB_USERNAME:-root}"
export MYSQL_PWD="${DB_PASSWORD:-}"

MIGRATIONS=(
  V1_6__document_center_schema.sql
  V1_7__governance_closed_loop_schema.sql
  V1_8__governance_standard_center.sql
  V1_9__governance_mapping_rules.sql
  V1_10__governance_scan_runs.sql
  V1_11__document_scope_and_relation.sql
  V1_12__governance_issue_timestamps.sql
  V1_13__document_collaboration.sql
)

mysql() { command mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USERNAME}" --default-character-set=utf8mb4 "$@"; }

mysql "${DB_NAME}" -e "CREATE TABLE IF NOT EXISTS schema_migration_applied (
  file_name VARCHAR(300) NOT NULL PRIMARY KEY,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" >/dev/null

# 本地镜像专用表（仅 local profile 使用，OceanBase 正本 schema 不含）：
# 通用操作日志，供 JdbcOperationLogStore 持久化资产/文档写操作审计。
mysql "${DB_NAME}" -e "CREATE TABLE IF NOT EXISTS operation_log_ext (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id VARCHAR(100) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NOT NULL DEFAULT '',
  target_id BIGINT NOT NULL DEFAULT 0,
  detail_json JSON NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_operation_log_ext_actor (actor_user_id, action, created_at),
  KEY idx_operation_log_ext_target (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Local operation log for JdbcOperationLogStore';" >/dev/null

applied_file() { # file_name
  local count
  count="$(mysql -N -B "${DB_NAME}" -e "SELECT COUNT(*) FROM schema_migration_applied WHERE file_name='$1';")"
  [[ "${count}" == "1" ]]
}

apply_through_python() { # file_name file_path
  python3 - "$1" "$2" <<'PY'
import os
import re
import subprocess
import sys

name, path = sys.argv[1], sys.argv[2]
host, port, user, db = os.environ["DB_HOST"], os.environ["DB_PORT"], os.environ["DB_USERNAME"], os.environ["DB_NAME"]
base = ["mysql", "-h", host, "-P", port, "-u", user, "--default-character-set=utf8mb4"]


def sql(script):
    return subprocess.run(base + [db], input=script, capture_output=True, text=True)


def col_exists(table, column):
    q = ("SELECT COUNT(*) FROM information_schema.COLUMNS "
         "WHERE TABLE_SCHEMA='%s' AND TABLE_NAME='%s' AND COLUMN_NAME='%s'" % (db, table, column))
    p = subprocess.run(base + ["-N", "-B", db, "-e", q], capture_output=True, text=True)
    return p.stdout.strip() == "1"


with open(path, encoding="utf-8") as fh:
    text = fh.read()

# 剔除纯注释行，避免注释并入相邻语句破坏 ALTER 守卫。
text = "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("--"))

# OceanBase 单引号字符串内的 \" 在 MySQL 中被当作转义（\" -> "），会破坏内嵌 JSON 的转义；
# 需要把反斜杠翻倍成 \\" ，使 MySQL 存出 \" ，JSON 列才合法。
text = text.replace('\\"', '\\\\"')

def split_sql(text):
    """按分号切分语句，忽略单引号字符串（含转义）内的分号。"""
    stmts, cur = [], []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "'":
            cur.append(c)
            i += 1
            while i < n:
                cur.append(text[i])
                if text[i] == "\\" and i + 1 < n:
                    cur.append(text[i + 1])
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c == ";":
            stmts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    if "".join(cur).strip():
        stmts.append("".join(cur))
    return [s for s in stmts if s.strip()]


statements = [s.strip() + ";" for s in split_sql(text)]

# 本地镜像适配（不改归档正本）：V1_11 的 document_scope 唯一键由 7 个 utf8mb4 列组成，
# 总长超过本机 MySQL 3072 字节键长上限（OceanBase 无此限制）。将 base_name 收窄为 150、
# process_section 收窄为 100（字典范围值远小于该长度），保持唯一约束与索引语义不变。
if name == "V1_11__document_scope_and_relation.sql":
    statements = [
        (s.replace("base_name VARCHAR(200) NULL,", "base_name VARCHAR(150) NULL,")
          .replace("process_section VARCHAR(200) NULL,", "process_section VARCHAR(100) NULL,"))
        if s.lstrip().upper().startswith("CREATE TABLE IF NOT EXISTS DOCUMENT_SCOPE")
        else s
        for s in statements
    ]
for stmt in statements:
    m = re.match(r"^\s*ALTER\s+TABLE\s+(\S+)\s+(.*)$", stmt, re.IGNORECASE | re.DOTALL)
    if m and re.search(r"ADD\s+COLUMN", m.group(2), re.IGNORECASE):
        table = m.group(1)
        cols = re.findall(r"ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z_][A-Za-z0-9_]*)",
                          m.group(2), re.IGNORECASE)
        if cols and all(col_exists(table, c) for c in cols):
            print(f"  跳过已存在列：ALTER TABLE {table} ({', '.join(cols)})")
            continue
        stmt = re.sub(r"ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS", "ADD COLUMN", stmt, flags=re.IGNORECASE)
    p = sql(stmt)
    if p.returncode != 0:
        snippet = " ".join(stmt.split())[:220]
        print(f"[失败] 语句执行出错：\n  {snippet}\n  {p.stderr.strip()}", file=sys.stderr)
        sys.exit(1)
print(f"  完成：{name}（{len(statements)} 条语句）")
PY
}

applied_any=0
for name in "${MIGRATIONS[@]}"; do
  file="${MIG_DIR}/${name}"
  if [[ ! -f "${file}" ]]; then
    echo "缺失迁移文件：${file}" >&2
    exit 1
  fi
  if applied_file "${name}"; then
    echo "跳过（已应用）：${name}"
    continue
  fi
  echo "应用：${name}"
  if ! apply_through_python "${name}" "${file}"; then
    echo "中止：${name} 应用失败；已应用的语句不会记录，可修正后重跑（重复列会被自动跳过）。" >&2
    exit 1
  fi
  mysql "${DB_NAME}" -e "INSERT INTO schema_migration_applied (file_name) VALUES ('${name}');" >/dev/null
  applied_any=1
done

echo "----"
if [[ "${applied_any}" == "1" ]]; then
  echo "补齐完成；已应用记录见 ${DB_NAME}.schema_migration_applied。"
else
  echo "无待补齐迁移（全部已应用）。"
fi
