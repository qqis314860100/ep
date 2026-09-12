#!/usr/bin/env bash
# 安装仓库自带的本地 git hooks（当前只有 pre-commit）。
#
# 用法：bash scripts/install-hooks.sh
#
# hook 本体版本化在 scripts/git-hooks/ 下，安装方式是软链到 .git/hooks/，
# 因此脚本更新后无需重装。`.git/` 不进版本库，新克隆需重跑本脚本一次。
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -n "$(git config core.hooksPath || true)" ]; then
  printf '注意：core.hooksPath 当前为 %s，.git/hooks 不会被使用。\n' "$(git config core.hooksPath)"
  printf '     先执行 git config --unset core.hooksPath，或把 hook 装到该目录。\n'
  exit 1
fi

HOOK_DIR="$(git rev-parse --git-path hooks)"
mkdir -p "$HOOK_DIR"
chmod +x scripts/git-hooks/*.sh scripts/git-hooks/pre-commit 2>/dev/null || true

for src in scripts/git-hooks/*; do
  [ -f "$src" ] || continue
  name="$(basename "$src")"
  dst="$HOOK_DIR/$name"
  if [ -e "$dst" ] && [ ! -L "$dst" ]; then
    backup="$dst.bak-$(date +%Y%m%d%H%M%S)"
    mv "$dst" "$backup"
    printf '已备份原 hook：%s\n' "$backup"
  fi
  ln -sfn "../../scripts/git-hooks/$name" "$dst"
  printf '已安装 %s -> scripts/git-hooks/%s\n' "$dst" "$name"
done

printf '\n'
if command -v gitleaks >/dev/null 2>&1; then
  printf '密钥扫描：gitleaks %s\n' "$(gitleaks version 2>/dev/null || echo '已安装')"
else
  printf '密钥扫描：未安装 gitleaks，使用内置规则（brew install gitleaks 可提升检出率）\n'
fi
printf '门禁内容：仓库结构白名单 + 密钥扫描 + frontend lint/typecheck（暂存 frontend/ 时）\n'
printf '绕过方式：git commit --no-verify\n'
