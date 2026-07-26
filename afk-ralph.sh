#!/bin/bash
set -euo pipefail

if ! [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Usage: $0 <positive-iteration-count>"
  exit 1
fi

if [ -n "$(git status --short)" ]; then
  echo "AFK Ralph stopped: worktree is not clean."
  git status --short
  exit 1
fi

for ((i=1; i<=$1; i++)); do
  echo "Starting Ralph iteration ${i}/$1"
  ./ralph-once.sh

  if grep -Fxq '<promise>COMPLETE</promise>' "${RALPH_LAST_MESSAGE_FILE:-/tmp/ep-data-governance-ralph-last.md}"; then
    echo "Plan complete after ${i} AFK iterations."
    exit 0
  fi
done

echo "Iteration limit reached without completion signal."
