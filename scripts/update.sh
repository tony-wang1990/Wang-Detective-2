#!/bin/bash

# Manual image update for Wang-Detective deployments.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
BACKUP_BEFORE_UPDATE="${BACKUP_BEFORE_UPDATE:-1}"
RUN_SMOKE_AFTER_UPDATE="${RUN_SMOKE_AFTER_UPDATE:-0}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-900}"
IMAGE="${KING_DETECTIVE_IMAGE:-}"

log() {
    printf '%s\n' "$*"
}

warn() {
    printf '[WARN] %s\n' "$*"
}

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

compose() {
    if docker compose version >/dev/null 2>&1; then
        docker compose "$@"
    elif command -v docker-compose >/dev/null 2>&1; then
        docker-compose "$@"
    else
        return 127
    fi
}

env_value() {
    key="$1"
    [ -f .env ] || return 0
    grep -E "^${key}=" .env | tail -n1 | cut -d= -f2- | tr -d '\r'
}

set_env_value() {
    key="$1"
    value="$2"
    touch .env
    if grep -q "^${key}=" .env; then
        sed -i "s|^${key}=.*|${key}=${value}|" .env
    else
        printf '%s=%s\n' "$key" "$value" >> .env
    fi
}

wait_for_health() {
    port="${SERVER_PORT:-$(env_value SERVER_PORT)}"
    port="${port:-9527}"
    health_url="http://127.0.0.1:${port}/actuator/health"
    started_at="$(date +%s)"
    while true; do
        body="$(curl -fsS --max-time 5 "$health_url" 2>/dev/null || true)"
        if echo "$body" | grep -q '"status":"UP"'; then
            log "健康检查通过: $health_url"
            return 0
        fi
        now="$(date +%s)"
        elapsed=$((now - started_at))
        if [ "$elapsed" -ge "$HEALTH_TIMEOUT_SECONDS" ]; then
            docker logs --tail 160 king-detective || true
            die "服务在 ${HEALTH_TIMEOUT_SECONDS}s 内未就绪"
        fi
        state="$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' king-detective 2>/dev/null || true)"
        log "  - 等待服务恢复... ${elapsed}s/${HEALTH_TIMEOUT_SECONDS}s ${state}"
        sleep 15
    done
}

[ -d "$APP_DIR" ] || die "应用目录不存在: $APP_DIR"
command -v docker >/dev/null 2>&1 || die "缺少 docker 命令"
command -v curl >/dev/null 2>&1 || die "缺少 curl 命令"

cd "$APP_DIR"

IMAGE="${IMAGE:-$(env_value KING_DETECTIVE_IMAGE)}"
IMAGE="${IMAGE:-ghcr.io/tony-wang1990/wang-detective-2:main}"

log "=== Wang-Detective 手动更新 ==="
log "应用目录: $APP_DIR"
log "目标镜像: $IMAGE"

mkdir -p runtime
current_image="$(docker inspect --format '{{.Config.Image}}' king-detective 2>/dev/null || true)"
current_image_id="$(docker inspect --format '{{.Image}}' king-detective 2>/dev/null || true)"
{
    echo "time=$(date '+%Y-%m-%d %H:%M:%S')"
    echo "image=$current_image"
    echo "image_id=$current_image_id"
} > runtime/last_image_before_update

if [ "$BACKUP_BEFORE_UPDATE" = "1" ] && [ -x scripts/backup.sh ]; then
    log "更新前备份..."
    scripts/backup.sh
else
    warn "已跳过更新前备份"
fi

set_env_value KING_DETECTIVE_IMAGE "$IMAGE"

log "拉取镜像..."
compose pull king-detective watcher

if [ -x scripts/sync-client-packages.sh ]; then
    log "同步 Windows/Android 安装包..."
    scripts/sync-client-packages.sh || warn "客户端安装包同步失败，已继续更新核心服务"
fi

log "重建服务..."
compose up -d --force-recreate king-detective watcher

wait_for_health

new_revision="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' king-detective 2>/dev/null || true)"
new_image_id="$(docker inspect --format '{{.Image}}' king-detective 2>/dev/null || true)"
{
    echo "time=$(date '+%Y-%m-%d %H:%M:%S')"
    echo "target_image=$IMAGE"
    echo "revision=${new_revision:-unknown}"
    echo "image_id=${new_image_id:-unknown}"
    echo "previous_image=$current_image"
    echo "previous_image_id=$current_image_id"
} > runtime/last_successful_update
log "更新完成。revision=${new_revision:-unknown} image_id=${new_image_id:-unknown}"

if [ "$RUN_SMOKE_AFTER_UPDATE" = "1" ] && [ -x scripts/server-smoke-test.sh ]; then
    log "执行冒烟检查..."
    scripts/server-smoke-test.sh
else
    warn "未自动执行完整冒烟检查；需要时运行: bash scripts/server-smoke-test.sh"
fi
