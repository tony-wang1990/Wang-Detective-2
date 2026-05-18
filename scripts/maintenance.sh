#!/bin/bash

# Unified maintenance entry for Wang-Detective server operations.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
ACTION="${1:-menu}"
shift || true

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

run_script() {
    script="$1"
    shift || true
    [ -x "$APP_DIR/scripts/$script" ] || die "脚本不存在或不可执行: $APP_DIR/scripts/$script"
    "$APP_DIR/scripts/$script" "$@"
}

show_menu() {
    cat <<'MENU'
=== Wang-Detective 维护入口 ===
1) 状态概览
2) 健康检查
3) 查看应用日志
4) 查看 watcher 日志
5) 创建备份
6) 恢复备份
7) 手动更新
8) 回滚镜像
9) 部署冒烟检查
10) 生成支持包
11) 重启服务
0) 退出
MENU
    printf '请选择: '
}

[ -d "$APP_DIR" ] || die "应用目录不存在: $APP_DIR"
cd "$APP_DIR"

case "$ACTION" in
    menu)
        while true; do
            show_menu
            read -r choice
            case "$choice" in
                1) compose ps; docker ps --filter name=king-detective ;;
                2) curl -fsS "http://127.0.0.1:${SERVER_PORT:-9527}/actuator/health" || true; echo ;;
                3) docker logs -f --tail 160 king-detective ;;
                4) docker logs -f --tail 160 king-detective-watcher ;;
                5) run_script backup.sh ;;
                6) printf '备份文件路径: '; read -r backup_file; RESTORE_CONFIRM=YES run_script restore.sh "$backup_file" ;;
                7) run_script update.sh ;;
                8) printf '目标镜像: '; read -r image; run_script rollback.sh "$image" ;;
                9) run_script server-smoke-test.sh ;;
                10) run_script support-bundle.sh ;;
                11) compose up -d --force-recreate king-detective watcher ;;
                0) exit 0 ;;
                *) echo "无效选择" ;;
            esac
        done
        ;;
    status)
        compose ps
        docker ps --filter name=king-detective
        ;;
    health)
        curl -fsS "http://127.0.0.1:${SERVER_PORT:-9527}/actuator/health"
        echo
        ;;
    logs)
        docker logs -f --tail "${1:-160}" king-detective
        ;;
    watcher-logs)
        docker logs -f --tail "${1:-160}" king-detective-watcher
        ;;
    backup)
        run_script backup.sh "$@"
        ;;
    restore)
        run_script restore.sh "$@"
        ;;
    update)
        run_script update.sh "$@"
        ;;
    rollback)
        run_script rollback.sh "$@"
        ;;
    smoke)
        run_script server-smoke-test.sh "$@"
        ;;
    support)
        run_script support-bundle.sh "$@"
        ;;
    restart)
        compose up -d --force-recreate king-detective watcher
        ;;
    *)
        cat <<USAGE
用法:
  bash scripts/maintenance.sh menu
  bash scripts/maintenance.sh status
  bash scripts/maintenance.sh health
  bash scripts/maintenance.sh logs [tail]
  bash scripts/maintenance.sh watcher-logs [tail]
  bash scripts/maintenance.sh backup
  bash scripts/maintenance.sh restore /path/to/backup.tar.gz
  bash scripts/maintenance.sh update
  bash scripts/maintenance.sh rollback ghcr.io/tony-wang1990/wang-detective:main
  bash scripts/maintenance.sh smoke
  bash scripts/maintenance.sh support
  bash scripts/maintenance.sh restart
USAGE
        exit 1
        ;;
esac
