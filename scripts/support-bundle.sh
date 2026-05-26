#!/bin/bash

# Collect a redacted support bundle for troubleshooting.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
SUPPORT_DIR="${SUPPORT_DIR:-$APP_DIR/support-bundles}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="${TMPDIR:-/tmp}/wang-detective-support-$TIMESTAMP.$$"
BUNDLE_FILE="$SUPPORT_DIR/wang-detective-support-$TIMESTAMP.tar.gz"

log() {
    printf '%s\n' "$*"
}

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

cleanup() {
    rm -rf "$WORK_DIR"
}

trap cleanup EXIT

redact_file() {
    src="$1"
    dst="$2"
    if [ -f "$src" ]; then
        redact_stream < "$src" > "$dst"
    fi
}

redact_stream() {
    sed -E \
        -e 's#(ADMIN_PASSWORD=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(WEB_PASSWORD=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(OPS_SSH_SECRET_KEY=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(TELEGRAM[^=]*TOKEN=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(BOT_TOKEN=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(OPENAI_API_KEY=)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#([Tt]oken[" ]*[:=][" ]*)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#([Pp]assword[" ]*[:=][" ]*)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#([Ss]ecret[-_ ]?[Kk]ey[" ]*[:=][" ]*)[^",[:space:]]+#\1***REDACTED***#g' \
        -e 's#(Authorization: Bearer )[A-Za-z0-9._-]+#\1***REDACTED***#g'
}

run_cmd() {
    name="$1"
    shift
    {
        echo "\$ $*"
        "$@" 2>&1 || true
    } | redact_stream > "$WORK_DIR/$name.txt"
}

[ -d "$APP_DIR" ] || die "应用目录不存在: $APP_DIR"
command -v tar >/dev/null 2>&1 || die "缺少 tar 命令"
mkdir -p "$SUPPORT_DIR" "$WORK_DIR"

cd "$APP_DIR"

log "=== Wang-Detective 支持包采集 ==="
log "应用目录: $APP_DIR"

redact_file "$APP_DIR/.env" "$WORK_DIR/env.redacted"
redact_file "$APP_DIR/application.yml" "$WORK_DIR/application.yml.redacted"
redact_file "$APP_DIR/docker-compose.yml" "$WORK_DIR/docker-compose.yml.redacted"

{
    echo "time=$TIMESTAMP"
    echo "app_dir=$APP_DIR"
    uname -a 2>/dev/null || true
} > "$WORK_DIR/system-summary.txt"

run_cmd docker-version docker version
run_cmd docker-ps docker ps -a
run_cmd docker-inspect-app docker inspect king-detective
run_cmd docker-inspect-watcher docker inspect king-detective-watcher
run_cmd docker-logs-app docker logs --tail 260 king-detective
run_cmd docker-logs-watcher docker logs --tail 260 king-detective-watcher
run_cmd df df -h
run_cmd free free -m
run_cmd health curl -fsS --max-time 10 "http://127.0.0.1:${SERVER_PORT:-9527}/actuator/health"

if docker compose version >/dev/null 2>&1; then
    run_cmd docker-compose-config docker compose config
    run_cmd docker-compose-ps docker compose ps
elif command -v docker-compose >/dev/null 2>&1; then
    run_cmd docker-compose-config docker-compose config
    run_cmd docker-compose-ps docker-compose ps
fi

if [ -f runtime/watcher_heartbeat ]; then
    cp runtime/watcher_heartbeat "$WORK_DIR/watcher_heartbeat"
fi

if [ -f logs/king-detective.log ]; then
    tail -n 300 logs/king-detective.log 2>&1 | redact_stream > "$WORK_DIR/king-detective-file-log-tail.txt" || true
fi

if [ -f logs/backup.log ]; then
    tail -n 200 logs/backup.log 2>&1 | redact_stream > "$WORK_DIR/backup-log-tail.txt" || true
fi

if [ -f runtime/last_image_before_update ]; then
    cp runtime/last_image_before_update "$WORK_DIR/last_image_before_update.txt"
fi

tar -czf "$BUNDLE_FILE" -C "$WORK_DIR" .
tar -tzf "$BUNDLE_FILE" >/dev/null || die "支持包校验失败，tar 无法读取: $BUNDLE_FILE"
chmod 600 "$BUNDLE_FILE" 2>/dev/null || true

log "支持包已生成: $BUNDLE_FILE"
log "注意: 已尽量脱敏 .env/application.yml，但发送给他人前仍建议自行检查。"
