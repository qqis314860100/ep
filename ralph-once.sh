#!/bin/bash
set -euo pipefail

LAST_MESSAGE_FILE="${RALPH_LAST_MESSAGE_FILE:-/tmp/ep-data-governance-ralph-last.md}"

if [ -n "$(git status --short)" ]; then
  echo "Ralph stopped: worktree is not clean."
  git status --short
  exit 1
fi

START_COMMIT=$(git rev-parse HEAD)
PROMPT=$(cat <<'EOF'
@AGENTS.md @docs/superpowers/plans/2026-07-26-data-governance-field-closure.md @data-governance-progress.md

继续实现数据治理闭环。严格遵守以下执行契约：
1. 阅读 AGENTS.md、计划中“下一任务”的完整章节和进度文件；仅按顺序完成进度文件中的下一项 Task。
2. Task 1-8 已完成并已提交，不得重复实现、改写或重新评审。
3. 用户明确要求控制 Token：不要调用任何 Superpowers 技能，不要创建子代理，不要做独立规格评审或质量评审；Ralph 已提供本轮所需执行流程，直接实现。
4. 本轮只完成一个 Task。先做该 Task 的最小相关验证，再按 AGENTS.md 扩大必要验证。
5. 使用 rtk 执行 Git、Maven、pnpm、构建和测试；前端只使用 pnpm。不要连接生产数据库。
6. 验证失败时停止，不提交，不继续其他 Task，并在最后消息中说明失败原因。
7. 验证通过后更新 data-governance-progress.md：勾选完成项、记录验证结果、把下一 Task 移到“下一任务”；提交号以 Git 历史为准。
8. 将本轮 Task 的代码和进度记录合并为且仅为一个符合仓库规则的中文 Conventional Commit；不要提交生成物或无关文件。
9. 提交后确认工作树干净。只有 Task 17 已完成且最终验证通过时，最后消息才可包含独占一行的 <promise>COMPLETE</promise>。

ONLY DO ONE TASK AT A TIME.
DO NOT COMMIT BEFORE VERIFICATION PASSES.
STOP ON FAILURE OR AN UNCLEAN WORKTREE.
EOF
)

codex exec \
  --cd "$(pwd)" \
  --sandbox danger-full-access \
  --output-last-message "${LAST_MESSAGE_FILE}" \
  "${PROMPT}"

END_COMMIT=$(git rev-parse HEAD)
if [ "${START_COMMIT}" = "${END_COMMIT}" ]; then
  echo "Ralph stopped: iteration created no commit."
  exit 1
fi
if [ "$(git rev-list --count "${START_COMMIT}..${END_COMMIT}")" -ne 1 ]; then
  echo "Ralph stopped: iteration must create exactly one commit."
  exit 1
fi
if [ -n "$(git status --short)" ]; then
  echo "Ralph stopped: iteration left a dirty worktree."
  git status --short
  exit 1
fi

echo "Ralph iteration complete: ${END_COMMIT}"
