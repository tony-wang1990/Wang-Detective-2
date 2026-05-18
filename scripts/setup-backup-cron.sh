#!/bin/bash

# Install or remove a daily backup cron entry on the deployment server.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
CRON_FILE="${CRON_FILE:-/etc/cron.d/wang-detective-backup}"
CRON_SCHEDULE="${CRON_SCHEDULE:-17 3 * * *}"
CRON_USER="${CRON_USER:-root}"
MODE="${1:-install}"

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

[ -d "$APP_DIR" ] || die "应用目录不存在: $APP_DIR"

case "$MODE" in
    install)
        [ -w "$(dirname "$CRON_FILE")" ] || die "无权写入 $CRON_FILE，请用 root 执行"
        cat > "$CRON_FILE" <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
${CRON_SCHEDULE} ${CRON_USER} cd ${APP_DIR} && bash scripts/backup.sh >> ${APP_DIR}/logs/backup.log 2>&1
EOF
        chmod 644 "$CRON_FILE"
        echo "已安装备份计划: $CRON_FILE"
        echo "计划时间: $CRON_SCHEDULE"
        ;;
    remove)
        rm -f "$CRON_FILE"
        echo "已删除备份计划: $CRON_FILE"
        ;;
    show)
        if [ -f "$CRON_FILE" ]; then
            cat "$CRON_FILE"
        else
            echo "未安装: $CRON_FILE"
        fi
        ;;
    *)
        echo "用法: bash scripts/setup-backup-cron.sh [install|remove|show]"
        exit 1
        ;;
esac
