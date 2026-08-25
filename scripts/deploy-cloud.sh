#!/usr/bin/env sh
set -eu

repo_dir="${1:-/opt/ai-mcp-gateway}"
cd "$repo_dir"

if [ ! -f .env.cloud ]; then
    echo "缺少 $repo_dir/.env.cloud，请先根据 .env.cloud.example 创建。" >&2
    exit 1
fi

# 默认从当前跟踪分支快进到最新提交；需要部署已同步的本地代码时可设置 SKIP_GIT_PULL=true。
if [ "${SKIP_GIT_PULL:-false}" != "true" ]; then
    git pull --ff-only
fi

compose() {
    docker compose --env-file .env.cloud -f compose.cloud.yaml "$@"
}

compose config --quiet
compose up -d --build --remove-orphans --wait --wait-timeout 240
compose ps
echo "部署提交：$(git rev-parse HEAD)"
