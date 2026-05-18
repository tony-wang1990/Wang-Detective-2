#!/bin/bash

# Restore Wang-Detective from a backup created by scripts/backup.sh.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
BACKUP_FILE="${1:-${BACKUP_FILE:-}}"
RESTORE_CONFIRM="${RESTORE_CONFIRM:-}"
SKIP_PRE_RESTORE_BACKUP="${SKIP_PRE_RESTORE_BACKUP:-0}"
START_AFTER_RESTORE="${START_AFTER_RESTORE:-1}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="${TMPDIR:-/tmp}/wang-detective-restore-$TIMESTAMP.$$"

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

[ -n "$BACKUP_FILE" ] || die "请指定备份文件: bash scripts/restore.sh /app/king-detective/backups/xxx.tar.gz"
[ -f "$BACKUP_FILE" ] || die "备份文件不存在: $BACKUP_FILE"
command -v tar >/dev/null 2>&1 || die "缺少 tar 命令"

mkdir -p "$APP_DIR" "$WORK_DIR"

log "=== Wang-Detective 恢复 ==="
log "应用目录: $APP_DIR"
log "备份文件: $BACKUP_FILE"

if tar -tzf "$BACKUP_FILE" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
    die "备份包包含不安全路径，拒绝恢复"
fi

if [ "$RESTORE_CONFIRM" != "YES" ]; then
    echo "恢复会覆盖当前 .env、application.yml、docker-compose.yml、data、keys、scripts。"
    printf '如确认恢复请输入 YES: '
    read -r answer
    [ "$answer" = "YES" ] || die "已取消恢复"
fi

cd "$APP_DIR"

if [ "$SKIP_PRE_RESTORE_BACKUP" != "1" ] && [ -x "$APP_DIR/scripts/backup.sh" ]; then
    log "恢复前先创建当前状态备份..."
    "$APP_DIR/scripts/backup.sh" || warn "恢复前备份失败，请确认是否继续"
fi

log "停止服务..."
compose stop king-detective watcher >/dev/null 2>&1 || true

tar -xzf "$BACKUP_FILE" -C "$WORK_DIR"
[ -d "$WORK_DIR/payload" ] || die "备份包格式不正确，缺少 payload 目录"

mkdir -p "$APP_DIR/.restore-archive-$TIMESTAMP"
for item in .env application.yml docker-compose.yml data keys scripts; do
    if [ -e "$APP_DIR/$item" ]; then
        mv "$APP_DIR/$item" "$APP_DIR/.restore-archive-$TIMESTAMP/$item"
    fi
    if [ -e "$WORK_DIR/payload/$item" ]; then
        cp -a "$WORK_DIR/payload/$item" "$APP_DIR/$item"
        log "  - 已恢复: $item"
    fi
done

if [ -d "$WORK_DIR/payload/logs" ]; then
    if [ -e "$APP_DIR/logs" ]; then
        mv "$APP_DIR/logs" "$APP_DIR/.restore-archive-$TIMESTAMP/logs"
    fi
    cp -a "$WORK_DIR/payload/logs" "$APP_DIR/logs"
    log "  - 已恢复: logs"
fi

chmod 600 "$APP_DIR/.env" 2>/dev/null || true
chmod +x "$APP_DIR"/scripts/*.sh 2>/dev/null || true

rm -rf "$WORK_DIR"

if [ "$START_AFTER_RESTORE" = "1" ]; then
    log "启动服务..."
    compose up -d king-detective watcher
    log "服务已启动，请稍后执行: bash scripts/server-smoke-test.sh"
else
    warn "START_AFTER_RESTORE=0，已恢复但未启动服务"
fi

log "恢复完成。旧文件已移动到: $APP_DIR/.restore-archive-$TIMESTAMP"
