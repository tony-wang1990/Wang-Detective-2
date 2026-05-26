#!/bin/bash

# Create a local Wang-Detective backup on the deployment server.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
INCLUDE_LOGS="${INCLUDE_LOGS:-0}"
INCLUDE_RUNTIME="${INCLUDE_RUNTIME:-0}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="${TMPDIR:-/tmp}/wang-detective-backup-$TIMESTAMP.$$"
BACKUP_NAME="wang-detective-backup-$TIMESTAMP.tar.gz"
BACKUP_FILE="$BACKUP_DIR/$BACKUP_NAME"

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

cleanup() {
    rm -rf "$WORK_DIR"
}

trap cleanup EXIT

compose() {
    if docker compose version >/dev/null 2>&1; then
        docker compose "$@"
    elif command -v docker-compose >/dev/null 2>&1; then
        docker-compose "$@"
    else
        return 127
    fi
}

copy_if_exists() {
    src="$1"
    dst="$2"
    if [ -e "$src" ]; then
        mkdir -p "$(dirname "$dst")"
        cp -a "$src" "$dst"
        log "  - 已加入备份: ${src#$APP_DIR/}"
    fi
}

command -v tar >/dev/null 2>&1 || die "缺少 tar 命令"
command -v date >/dev/null 2>&1 || die "缺少 date 命令"

[ -d "$APP_DIR" ] || die "应用目录不存在: $APP_DIR"
mkdir -p "$BACKUP_DIR" "$WORK_DIR/payload" "$WORK_DIR/meta"

cd "$APP_DIR"

log "=== Wang-Detective 备份 ==="
log "应用目录: $APP_DIR"
log "备份目录: $BACKUP_DIR"

copy_if_exists "$APP_DIR/.env" "$WORK_DIR/payload/.env"
copy_if_exists "$APP_DIR/application.yml" "$WORK_DIR/payload/application.yml"
copy_if_exists "$APP_DIR/docker-compose.yml" "$WORK_DIR/payload/docker-compose.yml"
copy_if_exists "$APP_DIR/data" "$WORK_DIR/payload/data"
copy_if_exists "$APP_DIR/keys" "$WORK_DIR/payload/keys"
copy_if_exists "$APP_DIR/scripts" "$WORK_DIR/payload/scripts"

if [ "$INCLUDE_LOGS" = "1" ]; then
    copy_if_exists "$APP_DIR/logs" "$WORK_DIR/payload/logs"
else
    warn "默认不打包 logs，避免备份过大；如需包含日志请设置 INCLUDE_LOGS=1"
fi

if [ "$INCLUDE_RUNTIME" = "1" ]; then
    copy_if_exists "$APP_DIR/runtime" "$WORK_DIR/payload/runtime"
fi

{
    echo "backup_time=$TIMESTAMP"
    echo "app_dir=$APP_DIR"
    echo "include_logs=$INCLUDE_LOGS"
    echo "include_runtime=$INCLUDE_RUNTIME"
    docker inspect --format 'image={{.Config.Image}} image_id={{.Image}} status={{.State.Status}}' king-detective 2>/dev/null || true
    docker inspect --format 'revision={{ index .Config.Labels "org.opencontainers.image.revision" }}' king-detective 2>/dev/null || true
} > "$WORK_DIR/meta/backup-info.txt"

docker ps --filter "name=king-detective" > "$WORK_DIR/meta/docker-ps.txt" 2>&1 || true
compose ps > "$WORK_DIR/meta/docker-compose-ps.txt" 2>&1 || true

tar -czf "$BACKUP_FILE" -C "$WORK_DIR" payload meta
chmod 600 "$BACKUP_FILE" 2>/dev/null || true

tar -tzf "$BACKUP_FILE" >/dev/null || die "备份包校验失败，tar 无法读取: $BACKUP_FILE"
tar -tzf "$BACKUP_FILE" | grep -q '^payload/' || die "备份包校验失败，缺少 payload 目录"
tar -tzf "$BACKUP_FILE" | grep -q '^meta/backup-info.txt$' || die "备份包校验失败，缺少 meta/backup-info.txt"

if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$BACKUP_FILE" > "$BACKUP_FILE.sha256"
fi

if [ "$RETENTION_DAYS" != "0" ] && command -v find >/dev/null 2>&1; then
    case "$BACKUP_DIR" in
        "$APP_DIR"/backups|"$APP_DIR"/backups/*)
            find "$BACKUP_DIR" -maxdepth 1 -name 'wang-detective-backup-*.tar.gz' -type f -mtime +"$RETENTION_DAYS" -print -delete 2>/dev/null || true
            find "$BACKUP_DIR" -maxdepth 1 -name 'wang-detective-backup-*.tar.gz.sha256' -type f -mtime +"$RETENTION_DAYS" -print -delete 2>/dev/null || true
            ;;
        *)
            warn "BACKUP_DIR 不在应用 backups 目录下，跳过自动清理"
            ;;
    esac
fi

log ""
log "备份完成: $BACKUP_FILE"
if [ -f "$BACKUP_FILE.sha256" ]; then
    log "校验文件: $BACKUP_FILE.sha256"
fi
