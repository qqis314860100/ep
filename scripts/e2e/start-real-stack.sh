#!/usr/bin/env bash
# 一键启动 ep 真链路栈（rag 真实能力 + ep 后端(默认真实客户端) + 前端 dev）。
#
# 端口考量：开发态 = Vite 前端 + Spring 后端两个进程两个端口，由 Vite proxy 把
# /api 转发给后端；rag(8000) 仅后端调用。生产态才合并单端口。
#
# 用法：
#   bash scripts/e2e/start-real-stack.sh
#   AI_RAG_DIR=/path/to/ai-rag bash scripts/e2e/start-real-stack.sh   # 默认 8080/5173
# 停止：kill "$(cat /tmp/ep-stack.pids)" 2>/dev/null; rm -f /tmp/ep-stack.pids

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AI_RAG_DIR="${AI_RAG_DIR:-/Users/tomtong/Software/js/ai-rag}"
PORT_BACK="${PORT_BACK:-8080}"
PORT_FRONT="${PORT_FRONT:-5173}"
RAG_URL="${RAG_URL:-http://127.0.0.1:8000}"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
PIDS_FILE=/tmp/ep-stack.pids
: > "${PIDS_FILE}"

echo "==> 检查 rag 能力服务 ${RAG_URL} ..."
RAG_KEY=""
if [[ -f "${AI_RAG_DIR}/.env" ]]; then
  RAG_KEY="$(grep '^RAG_API_KEY=' "${AI_RAG_DIR}/.env" | cut -d= -f2- || true)"
fi
if ! curl -sf -o /dev/null -H "X-Service-Key: ${RAG_KEY}" "${RAG_URL}/rag/health"; then
  echo "ERROR: rag 未就绪（${RAG_URL}）。请先启动 ai-rag 的 rag 服务（端口 8000）。" >&2
  exit 1
fi
echo "    rag OK"

echo "==> 启动 ep 后端 ${PORT_BACK}（默认真实能力客户端） ..."
(cd "${REPO_ROOT}/backend" && nohup env \
  JAVA_HOME="${JAVA_HOME}" \
  SERVER_PORT="${PORT_BACK}" \
  AI_CAPABILITY_BASE_URL="${RAG_URL}" \
  AI_CAPABILITY_API_KEY="${RAG_KEY}" \
  mvn -q spring-boot:run > /tmp/ep-backend.log 2>&1 & echo $! >> "${PIDS_FILE}")

echo "==> 等待后端就绪 ..."
for _ in $(seq 1 60); do
  if curl -sf -o /dev/null "http://127.0.0.1:${PORT_BACK}/actuator/health"; then break; fi
  sleep 2
done
curl -sf -o /dev/null "http://127.0.0.1:${PORT_BACK}/actuator/health" \
  || { echo "ERROR: 后端启动失败，见 /tmp/ep-backend.log" >&2; exit 1; }
echo "    backend OK"

echo "==> 启动前端 ${PORT_FRONT}（代理 /api -> ${PORT_BACK}） ..."
(cd "${REPO_ROOT}/frontend" && nohup env \
  VITE_PROXY_TARGET="http://127.0.0.1:${PORT_BACK}" \
  pnpm dev --port "${PORT_FRONT}" --strictPort > /tmp/ep-frontend.log 2>&1 & echo $! >> "${PIDS_FILE}")

for _ in $(seq 1 30); do
  if curl -sf -o /dev/null "http://localhost:${PORT_FRONT}/"; then break; fi
  sleep 1
done
curl -sf -o /dev/null "http://localhost:${PORT_FRONT}/" \
  || { echo "ERROR: 前端启动失败，见 /tmp/ep-frontend.log" >&2; exit 1; }
echo "    frontend OK"

echo
echo "=============================================================="
echo " 真链路已就绪：登录 emp-admin / demo123"
echo "   UI:    http://localhost:${PORT_FRONT}"
echo "   后端:  http://127.0.0.1:${PORT_BACK}（默认真实能力客户端）"
echo "   rag:   ${RAG_URL}"
echo " 判别：/ai 问答首句若是固定「来自 Fake 能力服务的回答。」= Fake；"
echo "       否则为真实回答且带引用。建议名称/描述来自真实抽取即真实链路。"
echo " 切回 Fake（离线/演示）：重启后端时加 AI_CAPABILITY_MOCK=true"
echo " 停止：kill \"\$(cat ${PIDS_FILE})\" 2>/dev/null; rm -f ${PIDS_FILE}"
echo "=============================================================="
