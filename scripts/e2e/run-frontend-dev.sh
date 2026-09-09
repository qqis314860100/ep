#!/usr/bin/env bash
# 前台启动 ep 前端 dev（代理 /api -> 8080 后端）。
# 用法：bash scripts/e2e/run-frontend-dev.sh    （Ctrl+C 停止）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORT_FRONT="${PORT_FRONT:-5173}"
PORT_BACK="${PORT_BACK:-8080}"

echo "启动前端 http://localhost:${PORT_FRONT}（/api -> 127.0.0.1:${PORT_BACK}）…（Ctrl+C 停止）"
cd "${REPO_ROOT}/frontend"
exec env VITE_PROXY_TARGET="http://127.0.0.1:${PORT_BACK}" pnpm dev --port "${PORT_FRONT}"
