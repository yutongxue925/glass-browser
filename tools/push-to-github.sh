#!/usr/bin/env bash
# 一键把本工程推送到 GitHub（玻璃浏览器）
#
# 用法：
#   bash tools/push-to-github.sh [owner/repo] [branch]
# 默认：yutongxue925/glass-browser  main
#
# 前提：GitHub 上**已存在**名为 glass-browser 的空仓库
#       （用连接器建仓需要 administration 权限，当前连接器 Token 没有，
#         所以这一步由你在网页上点一下：https://github.com/new?name=glass-browser ）
#
# 安全：Token 只通过环境变量 + git credential helper 传递，
#       不会写进 remote URL，也不会被打印出来。
set -euo pipefail

SLUG="${1:-yutongxue925/glass-browser}"
BRANCH="${2:-main}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN_SCRIPT="/root/.codebuddy/skills/github-connector/scripts/get_token.sh"

# 1) 取 Token
if [ -z "${GITHUB_TOKEN:-}" ]; then
  if [ -f "$TOKEN_SCRIPT" ]; then
    # shellcheck disable=SC1090
    source "$TOKEN_SCRIPT" github >/dev/null 2>&1 || true
  fi
fi
[ -n "${GITHUB_TOKEN:-}" ] || { echo "❌ 拿不到 GitHub Token：请在 CodeBuddy「连接器」里重新授权 GitHub"; exit 1; }

cd "$ROOT"

# 2) 确认远端仓库存在（避免推到一个不存在的地址后报一堆无关错误）
code=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${GITHUB_TOKEN}" \
       "https://api.github.com/repos/${SLUG}")
if [ "$code" != "200" ]; then
  echo "❌ 仓库 ${SLUG} 不可访问（HTTP $code）。"
  echo "   请先在 GitHub 上创建空仓库：https://github.com/new?name=${SLUG##*/}"
  exit 1
fi
echo "✓ 仓库存在：${SLUG}"

# 3) 配置远端（URL 里不含 Token）
git remote remove origin >/dev/null 2>&1 || true
git remote add origin "https://github.com/${SLUG}.git"

# 4) 用 credential helper 临时喂 Token，避免写进 .git/config
git config --local credential.helper \
  '!f() { echo "username=oauth2"; echo "password=${GITHUB_TOKEN}"; }; f'

# 5) 提交（若有未提交改动）并推送
if ! git diff --quiet || ! git diff --cached --quiet; then
  git add -A
  git commit -q -m "chore: 更新（由 push-to-github.sh 自动提交）"
fi

echo "→ 推送到 ${SLUG} (${BRANCH}) ..."
git push -u origin "$BRANCH"

# 6) 收尾：清掉 helper，避免 Token 残留在本地配置里
git config --local --unset credential.helper || true

echo
echo "✓ 完成：https://github.com/${SLUG}"
