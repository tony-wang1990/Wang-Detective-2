#!/bin/bash

# Local/CI release verification helper. It runs only checks available in the current environment.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_FRONTEND="${RUN_FRONTEND:-1}"
RUN_MAVEN="${RUN_MAVEN:-1}"
RUN_SHELLCHECK="${RUN_SHELLCHECK:-0}"

log() {
    printf '%s\n' "$*"
}

warn() {
    printf '[WARN] %s\n' "$*"
}

run() {
    log ">>> $*"
    "$@"
}

cd "$ROOT_DIR"

log "=== Wang-Detective 发布前轻量验证 ==="

if command -v git >/dev/null 2>&1; then
    run git diff --check
fi

if command -v bash >/dev/null 2>&1; then
    for script in scripts/*.sh; do
        [ -f "$script" ] || continue
        run bash -n "$script"
    done
fi

if command -v grep >/dev/null 2>&1; then
    crlf_files="$(grep -Il $'\r' scripts/*.sh 2>/dev/null || true)"
    if [ -n "$crlf_files" ]; then
        printf '%s\n' "$crlf_files"
        echo "脚本包含 CRLF 换行，请先转换为 LF"
        exit 1
    fi
fi

if command -v rg >/dev/null 2>&1 && [ -d frontend/src ]; then
    if rg -n "window\\.(alert|confirm|prompt)\\(|\\b(alert|confirm|prompt)\\(" frontend/src; then
        echo "前端源码仍存在浏览器原生 alert/confirm/prompt，请改为项目内弹窗或 toast"
        exit 1
    fi
fi

if command -v node >/dev/null 2>&1 && [ -f scripts/verify-ui-api-mapping.mjs ]; then
    run node scripts/verify-ui-api-mapping.mjs
fi

if command -v node >/dev/null 2>&1 && [ -f scripts/acceptance-check.mjs ]; then
    run node scripts/acceptance-check.mjs
fi

if [ "$RUN_SHELLCHECK" = "1" ]; then
    if command -v shellcheck >/dev/null 2>&1; then
        run shellcheck scripts/*.sh
    else
        warn "shellcheck 不可用，已跳过"
    fi
fi

if [ "$RUN_FRONTEND" = "1" ]; then
    if command -v npm >/dev/null 2>&1 && [ -f frontend/package.json ]; then
        run npm --prefix frontend run build
    else
        warn "npm 或 frontend/package.json 不可用，已跳过前端构建"
    fi
fi

if [ "$RUN_MAVEN" = "1" ]; then
    if command -v mvn >/dev/null 2>&1 && [ -f pom.xml ]; then
        run mvn -DskipTests package
    else
        warn "mvn 或 pom.xml 不可用，已跳过 Maven 构建"
    fi
fi

log "验证脚本执行完成"
