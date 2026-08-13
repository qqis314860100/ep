#!/usr/bin/env bash
# ============================================================================
# run-e2e.sh — 从 0 启动后端 + 前端，跑完整业务全流程 E2E 测试
#
# 流程：
#   1. 生成 mock 文件（PDF / X_T / TXT / PNG）
#   2. 启动后端（dev profile，内存仓储，端口 8080），等待健康检查
#   3. 启动前端（vite dev，端口 5173，VITE_USE_MOCKS=false 走真实 API）
#   4. 执行全流程脚本 flow.mjs（上传 → 资产 → 治理闭环 → 文档 → 关联 → 收藏评论 → 统一检索）
#   5. 打印 PASS/FAIL 汇总并退出
#
# 用法：bash scripts/e2e/run-e2e.sh
# 环境变量：
#   SKIP_FRONTEND=1    跳过前端启动（只测后端 API 闭环）
#   KEEP_SERVERS=1     结束后不关停后端/前端
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
E2E_DIR="$ROOT/scripts/e2e"
LOG_DIR="$E2E_DIR/.logs"
mkdir -p "$LOG_DIR"

BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
FLOW_LOG="$LOG_DIR/flow.log"
BACKEND_PID=""
FRONTEND_PID=""

PORT_BACKEND="${SERVER_PORT:-8080}"
PORT_FRONTEND=5173

echo "==> [1/5] 生成 mock 文件"
node "$E2E_DIR/mock-files.mjs"

# ---------------------------------------------------------------------------
# 启动后端
# ---------------------------------------------------------------------------
echo "==> [2/5] 启动后端 (dev profile, :$PORT_BACKEND)"
(
  cd "$ROOT/backend"
  mvn -q spring-boot:run -Dspring-boot.run.profiles=dev > "$BACKEND_LOG" 2>&1
) &
BACKEND_PID=$!

echo "    等待后端健康检查 GET :$PORT_BACKEND/actuator/health ..."
backend_ready=0
for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:$PORT_BACKEND/actuator/health" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "    [FAIL] 后端进程已退出，日志尾部："
    tail -20 "$BACKEND_LOG"
    exit 1
  fi
  sleep 1
done
if [ "$backend_ready" -ne 1 ]; then
  echo "    [FAIL] 后端 120s 内未就绪，日志尾部："
  tail -30 "$BACKEND_LOG"
  exit 1
fi
echo "    后端就绪：$(curl -sf "http://127.0.0.1:$PORT_BACKEND/actuator/health")"

# ---------------------------------------------------------------------------
# 启动前端（可选）
# ---------------------------------------------------------------------------
if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  echo "==> [3/5] 启动前端 (vite dev, :$PORT_FRONTEND, VITE_USE_MOCKS=false)"
  (
    cd "$ROOT/frontend"
    VITE_USE_MOCKS=false VITE_API_BASE_URL= pnpm dev --host 127.0.0.1 --port "$PORT_FRONTEND" > "$FRONTEND_LOG" 2>&1
  ) &
  FRONTEND_PID=$!

  frontend_ready=0
  for _ in $(seq 1 90); do
    if curl -sf "http://127.0.0.1:$PORT_FRONTEND/" >/dev/null 2>&1; then
      frontend_ready=1
      break
    fi
    if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if [ "$frontend_ready" -ne 1 ]; then
    # 前端就绪失败不阻断 API 全流程；flow.mjs 的前端冒烟步骤会单独报告
    echo "    [WARN] 前端 90s 内未就绪（vite 日志尾部）："
    tail -20 "$FRONTEND_LOG"
  else
    echo "    前端就绪：http://127.0.0.1:$PORT_FRONTEND/"
  fi
else
  echo "==> [3/5] 跳过前端（SKIP_FRONTEND=1）"
fi

# ---------------------------------------------------------------------------
# 跑全流程脚本
# ---------------------------------------------------------------------------
echo "==> [4/5] 执行全流程脚本 flow.mjs"
set +e
node "$E2E_DIR/flow.mjs" --backend "http://127.0.0.1:$PORT_BACKEND" --frontend "http://127.0.0.1:$PORT_FRONTEND" 2>&1 | tee "$FLOW_LOG"
FLOW_EXIT=${PIPESTATUS[0]}
set -e

echo "==> [5/5] 汇总"
if [ "$FLOW_EXIT" -eq 0 ]; then
  echo "✅ E2E 全流程通过"
else
  echo "❌ E2E 全流程失败 (exit=$FLOW_EXIT)，完整日志见 $FLOW_LOG"
fi

# ---------------------------------------------------------------------------
# 关停
# ---------------------------------------------------------------------------
if [ "${KEEP_SERVERS:-0}" != "1" ]; then
  echo "    关停服务..."
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null || true
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
  # mvn/vite 会 fork 子进程，兜底杀掉仍占用端口的进程
  lsof -tiTCP:"$PORT_BACKEND" -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
  lsof -tiTCP:"$PORT_FRONTEND" -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
  wait 2>/dev/null || true
  echo "    已关停。日志：backend=$BACKEND_LOG frontend=$FRONTEND_LOG flow=$FLOW_LOG"
else
  echo "    KEEP_SERVERS=1，服务保持运行：backend pid=$BACKEND_PID frontend pid=$FRONTEND_PID"
fi

exit "$FLOW_EXIT"
