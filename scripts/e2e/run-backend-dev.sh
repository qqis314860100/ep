#!/usr/bin/env bash
# 前台启动 ep 后端（默认真实能力链路，实时日志滚动）。
# 自动：定位 Java21 → 读 ai-rag .env 的 RAG_API_KEY → 以环境变量传给 mvn spring-boot:run。
# 用法：bash scripts/e2e/run-backend-dev.sh     （Ctrl+C 停止）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AI_RAG_DIR="${AI_RAG_DIR:-/Users/tomtong/Software/js/ai-rag}"
PORT_BACK="${PORT_BACK:-8080}"
RAG_URL="${RAG_URL:-http://127.0.0.1:8000}"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

RAG_KEY=""
if [[ -f "${AI_RAG_DIR}/.env" ]]; then
  RAG_KEY="$(grep '^RAG_API_KEY=' "${AI_RAG_DIR}/.env" | cut -d= -f2- || true)"
fi

if ! curl -sf -o /dev/null -H "X-Service-Key: ${RAG_KEY}" "${RAG_URL}/rag/health"; then
  echo "提示：rag(${RAG_URL}) 未就绪，问答/编目会报能力服务错误；仍继续启动（仅后端）。" >&2
fi

echo "启动后端 http://127.0.0.1:${PORT_BACK}（真实能力 ${RAG_URL}）…（Ctrl+C 停止）"
cd "${REPO_ROOT}/backend"
exec env \
  JAVA_HOME="${JAVA_HOME}" \
  SERVER_PORT="${PORT_BACK}" \
  AI_CAPABILITY_BASE_URL="${RAG_URL}" \
  AI_CAPABILITY_API_KEY="${RAG_KEY}" \
  mvn spring-boot:run
