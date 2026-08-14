#!/bin/bash
# Repository structure hygiene check.
#
# Keeps the repository root whitelisted: any unexpected file, directory, or
# cache artifact at the root fails the check. Run before committing
# (see AGENTS.md -> Repository Structure).
set -u
shopt -s nullglob

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

violations=0
report() {
  printf 'FAIL %s\n' "$1"
  violations=$((violations + 1))
}

for entry in .* *; do
  case "$entry" in
    .|..)
      continue
      ;;
    # Allowed directories
    .ai|.claude|.git|.prompt|backend|docs|frontend|scripts)
      continue
      ;;
    # Allowed dotfiles and root-level sources
    .editorconfig|.env.example|.env.local|.gitignore|AGENTS.md|CONTEXT.md|README.md|requirement.md)
      continue
      ;;
    # Script-generated requirement docx only
    仿真数模资产管理系统_产品需求文档_V*.docx)
      continue
      ;;
    # Known cache, tool, or artifact directories that must never appear at the root
    .pnpm-store|.playwright-cli|.superpowers|.worktrees|output|node_modules|dist|target|.data|.DS_Store)
      report "cache/artifact at root: '$entry' (delete it; generated artifacts belong in /tmp or scripts/e2e/.logs/)"
      ;;
    *.log|*.tmp|*.bak|*.pdf)
      report "stray file at root: '$entry' (move it under docs/ or /tmp)"
      ;;
    *.md)
      report "stray markdown at root: '$entry' (new docs belong under docs/)"
      ;;
    *)
      report "unexpected root entry: '$entry' (put it under docs/, scripts/, backend/, frontend/, or .ai/)"
      ;;
  esac
done

if [ "$violations" -gt 0 ]; then
  printf 'Structure check FAILED (%d issue(s)); fix them before committing.\n' "$violations"
  exit 1
fi
printf 'Structure check PASS: repository root is clean.\n'
