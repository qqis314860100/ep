#!/usr/bin/env bash
# Claude Code PreToolUse 门禁：把 Write/Edit 落到不可逆文件上的权限决策改为「需人工确认」。
#
# 覆盖两类（见 AGENTS.md 硬规则）：
#   ① scripts/db/migrations/ 下的迁移 SQL —— 表结构变更不可逆
#   ② pom.xml / package.json / 锁文件 —— 新增依赖需先说明理由
#
# 由 .claude/settings.json 的 PreToolUse 注册。只把决策改成 ask，
# 不做 allow/deny：是否放行留给人类判断，本脚本不代替决定。
set -u

input="$(cat)"
path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"
[ -n "$path" ] || exit 0

# 依赖清单也会出现在 node_modules 里，那不是本仓库的依赖声明。
case "$path" in
  */node_modules/*) exit 0 ;;
esac

reason=""
case "$path" in
  scripts/db/migrations/*|*/scripts/db/migrations/*)
    reason="迁移 SQL 属于不可逆的 schema 变更。按 AGENTS.md 硬规则：先在 spec 或 docs/plans/ 留下记录再改。"
    ;;
  *)
    case "${path##*/}" in
      pom.xml|package.json|pnpm-lock.yaml|pnpm-lock.yml|package-lock.json|yarn.lock)
        reason="依赖清单变更会引入或升级依赖。按 AGENTS.md 硬规则：在 spec 或提交正文写明理由。"
        ;;
      *) exit 0 ;;
    esac
    ;;
esac

jq -n --arg r "$reason" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "ask",
    permissionDecisionReason: $r
  }
}'
exit 0
