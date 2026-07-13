#!/bin/bash

# Server-side smoke test for Wang-Detective deployments.
# Keep this script with LF line endings. It is intended to run on the VPS after install.sh.

set -u

APP_DIR="${APP_DIR:-/app/king-detective}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
SERVER_PORT="${SERVER_PORT:-}"
if [ -z "$SERVER_PORT" ] && [ -f "$ENV_FILE" ]; then
    SERVER_PORT="$(grep -E '^SERVER_PORT=' "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r')"
fi
SERVER_PORT="${SERVER_PORT:-9527}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${SERVER_PORT}}"
OCI_CFG_ID="${OCI_CFG_ID:-}"
TMP_DIR="${TMPDIR:-/tmp}/wang-detective-smoke.$$"

PASS_COUNT=0
WARN_COUNT=0
FAIL_COUNT=0

mkdir -p "$TMP_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    printf '[PASS] %s\n' "$*"
}

warn() {
    WARN_COUNT=$((WARN_COUNT + 1))
    printf '[WARN] %s\n' "$*"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    printf '[FAIL] %s\n' "$*"
}

need_cmd() {
    if command -v "$1" >/dev/null 2>&1; then
        pass "命令可用: $1"
    else
        fail "缺少命令: $1"
    fi
}

env_value() {
    key="$1"
    [ -f "$ENV_FILE" ] || return 0
    grep -E "^${key}=" "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r' | sed 's/^"//;s/"$//;s/^'\''//;s/'\''$//'
}

first_env_value() {
    for key in "$@"; do
        value="$(env_value "$key")"
        if [ -n "$value" ]; then
            printf '%s' "$value"
            return 0
        fi
    done
    return 0
}

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

extract_json_string() {
    key="$1"
    sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
}

http_code() {
    path="$1"
    output="$TMP_DIR/http-code-body"
    curl -sS --max-time 12 -o "$output" -w '%{http_code}' "$BASE_URL$path" 2>/dev/null || true
}

api_get() {
    path="$1"
    token="$2"
    if [ -n "$token" ]; then
        curl -fsS --max-time 15 -H "Authorization: Bearer ${token}" "$BASE_URL$path" 2>/dev/null || true
    else
        curl -fsS --max-time 15 "$BASE_URL$path" 2>/dev/null || true
    fi
}

api_post() {
    path="$1"
    token="$2"
    body="$3"
    if [ -n "$token" ]; then
        curl -fsS --max-time 20 -X POST "$BASE_URL$path" \
            -H 'Content-Type: application/json' \
            -H "Authorization: Bearer ${token}" \
            -d "$body" 2>/dev/null || true
    else
        curl -fsS --max-time 20 -X POST "$BASE_URL$path" \
            -H 'Content-Type: application/json' \
            -d "$body" 2>/dev/null || true
    fi
}

check_api_success() {
    name="$1"
    body="$2"
    if echo "$body" | grep -q '"success"[[:space:]]*:[[:space:]]*true'; then
        pass "$name 接口返回 success=true"
    elif [ -n "$body" ]; then
        warn "$name 接口有返回但不是 success=true: $(printf '%s' "$body" | cut -c 1-180)"
    else
        fail "$name 接口无有效返回"
    fi
}

echo "=== Wang-Detective 服务器冒烟检查 ==="
echo "应用目录: $APP_DIR"
echo "检查地址: $BASE_URL"
echo ""

need_cmd docker
need_cmd curl
need_cmd grep
need_cmd sed
need_cmd date
need_cmd tar
need_cmd od

if [ ! -d "$APP_DIR" ]; then
    fail "应用目录不存在: $APP_DIR"
else
    pass "应用目录存在: $APP_DIR"
    cd "$APP_DIR" || fail "无法进入应用目录: $APP_DIR"
fi

if docker compose version >/dev/null 2>&1; then
    pass "Docker Compose v2 可用"
    compose() { docker compose "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
    warn "正在使用旧 docker-compose，建议升级到 Docker Compose Plugin v2"
    compose() { docker-compose "$@"; }
else
    fail "Docker Compose 不可用"
    compose() { return 1; }
fi

echo ""
echo "=== 文件和配置 ==="
for file in docker-compose.yml application.yml .env; do
    if [ -f "$file" ]; then
        pass "配置文件存在: $file"
    else
        fail "配置文件缺失: $file"
    fi
done

EXPECTED_SCRIPTS="
watcher.sh
server-smoke-test.sh
backup.sh
restore.sh
update.sh
rollback.sh
support-bundle.sh
maintenance.sh
setup-backup-cron.sh
verify-release.sh
"

for script_name in $EXPECTED_SCRIPTS; do
    script_path="scripts/${script_name}"
    if [ -d "$script_path" ]; then
        fail "运维脚本路径异常，是目录不是文件: $script_path"
    elif [ -f "$script_path" ]; then
        pass "运维脚本存在: $script_path"
        if [ -x "$script_path" ]; then
            pass "运维脚本可执行: $script_path"
        else
            warn "运维脚本不可执行: $script_path，可执行 chmod +x scripts/*.sh"
        fi
        if command -v od >/dev/null 2>&1; then
            hex_bytes="$(od -An -t x1 "$script_path")"
            case " $hex_bytes " in
                *" 0d "*) fail "运维脚本包含 CR 字节，bash 可能报错: $script_path" ;;
            esac
        fi
        if command -v bash >/dev/null 2>&1; then
            if bash -n "$script_path" >/dev/null 2>&1; then
                pass "运维脚本语法通过: $script_path"
            else
                fail "运维脚本语法失败: $script_path"
            fi
        fi
    else
        fail "运维脚本缺失: $script_path"
    fi
done

EXPECTED_NODE_HELPERS="
remote-smoke-test.mjs
"

for helper_name in $EXPECTED_NODE_HELPERS; do
    helper_path="scripts/${helper_name}"
    if [ -f "$helper_path" ]; then
        pass "Node 辅助脚本存在: $helper_path"
        if command -v node >/dev/null 2>&1; then
            if node --check "$helper_path" >/dev/null 2>&1; then
                pass "Node 辅助脚本语法通过: $helper_path"
            else
                fail "Node 辅助脚本语法失败: $helper_path"
            fi
        else
            warn "node 不可用，已跳过 Node 辅助脚本语法检查: $helper_path"
        fi
    else
        warn "Node 辅助脚本缺失: $helper_path，服务器仍可使用 shell 版远程 smoke"
    fi
done

if [ -f src/main/resources/dist/index.html ] || [ -f dist/index.html ]; then
    pass "前端生产入口存在"
else
    warn "未在应用目录发现前端生产入口，容器内镜像可能正常，但当前挂载目录无法直接验证 dist"
fi

if [ -f docker-compose.yml ]; then
    if grep -q 'ghcr.io/tony-wang1990/wang-detective-2:main' docker-compose.yml; then
        pass "compose 使用 Wang-Detective GHCR 镜像"
    else
        warn "compose 镜像不是 ghcr.io/tony-wang1990/wang-detective-2:main，请确认是否为自定义部署"
    fi
    if grep -q 'king-detective-watcher' docker-compose.yml; then
        pass "compose 已包含自动更新 watcher"
    else
        fail "compose 未包含 king-detective-watcher，一键更新不可用"
    fi
    if grep -Fq './backups:/app/king-detective/backups' docker-compose.yml && grep -Fq './scripts:/app/king-detective/scripts:ro' docker-compose.yml; then
        pass "compose 已挂载 backups 和 scripts，Web 备份恢复入口可读取脚本"
    else
        fail "compose 未挂载 backups/scripts，Web 备份恢复入口不可用"
    fi
    if compose config >/dev/null 2>&1; then
        pass "docker compose 配置可解析"
    else
        fail "docker compose 配置解析失败"
    fi
fi

ADMIN_USERNAME="$(first_env_value ADMIN_USERNAME WEB_ACCOUNT)"
ADMIN_PASSWORD="$(first_env_value ADMIN_PASSWORD WEB_PASSWORD)"
TG_TOKEN="$(first_env_value TELEGRAM_BOT_TOKEN BOT_TOKEN)"
TG_CHAT_ID="$(first_env_value TELEGRAM_BOT_CHAT_ID TELEGRAM_CHAT_ID TG_CHAT_ID)"

if [ -n "$ADMIN_USERNAME" ] && [ -n "$ADMIN_PASSWORD" ]; then
    pass "管理员账号环境变量已配置"
    if [ "$ADMIN_PASSWORD" = "admin123456" ]; then
        warn "管理员密码仍为默认值 admin123456，生产环境请立即修改"
    fi
else
    fail ".env 未找到 ADMIN_USERNAME/ADMIN_PASSWORD"
fi

if [ -n "$TG_TOKEN" ] && [ -n "$TG_CHAT_ID" ]; then
    pass "Telegram Bot Token 和 Chat ID 已配置"
else
    warn "Telegram Bot Token 或 Chat ID 未完整配置，Bot 菜单和告警不可用"
fi

echo ""
echo "=== 容器状态 ==="
APP_STATUS="$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' king-detective 2>/dev/null || true)"
if [ -n "$APP_STATUS" ]; then
    if echo "$APP_STATUS" | grep -q '^running'; then
        pass "king-detective 容器运行中: $APP_STATUS"
    else
        fail "king-detective 容器状态异常: $APP_STATUS"
    fi
    APP_REVISION="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' king-detective 2>/dev/null || true)"
    APP_IMAGE="$(docker inspect --format '{{.Config.Image}}' king-detective 2>/dev/null || true)"
    [ -n "$APP_IMAGE" ] && pass "运行镜像: $APP_IMAGE"
    [ -n "$APP_REVISION" ] && pass "镜像提交: ${APP_REVISION}"
else
    fail "找不到 king-detective 容器"
fi

WATCHER_STATUS="$(docker inspect --format '{{.State.Status}}' king-detective-watcher 2>/dev/null || true)"
if [ -z "$WATCHER_STATUS" ]; then
    fail "找不到 king-detective-watcher 容器，一键更新无法执行"
elif [ "$WATCHER_STATUS" = "running" ]; then
    pass "king-detective-watcher 容器运行中"
else
    fail "king-detective-watcher 容器状态异常: $WATCHER_STATUS"
fi

if [ -f runtime/watcher_heartbeat ]; then
    hb_mtime="$(stat -c %Y runtime/watcher_heartbeat 2>/dev/null || echo 0)"
    now_ts="$(date +%s)"
    hb_age=$((now_ts - hb_mtime))
    if [ "$hb_age" -le 180 ]; then
        pass "watcher 心跳正常，${hb_age}s 前更新"
    else
        warn "watcher 心跳较旧，${hb_age}s 前更新"
    fi
else
    warn "未发现 runtime/watcher_heartbeat，watcher 可能刚启动或未正常运行"
fi

WATCHER_LOGS="$(docker logs --tail 100 king-detective-watcher 2>&1 || true)"
if echo "$WATCHER_LOGS" | grep -Eiq 'No such container|Cannot connect|permission denied|Error response from daemon'; then
    fail "watcher 最近日志包含 Docker 错误，请执行 docker logs -f king-detective-watcher 查看"
elif [ -n "$WATCHER_LOGS" ]; then
    pass "watcher 最近日志未发现明显 Docker 错误"
else
    warn "watcher 日志为空，可能刚启动"
fi

echo ""
echo "=== HTTP 和 API ==="
HEALTH_BODY="$(api_get /actuator/health '')"
if echo "$HEALTH_BODY" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    HEALTH_VERSION="$(printf '%s' "$HEALTH_BODY" | extract_json_string version)"
    if [ -n "$HEALTH_VERSION" ]; then
        pass "健康检查 UP，版本: $HEALTH_VERSION"
        if [ -n "${APP_REVISION:-}" ]; then
            HEALTH_SHORT_REVISION="${APP_REVISION:0:7}"
            if echo "$HEALTH_VERSION" | grep -Fq "$HEALTH_SHORT_REVISION"; then
                pass "健康版本和镜像提交一致: $HEALTH_VERSION"
            else
                warn "健康版本和镜像提交可能不一致: health=$HEALTH_VERSION image_revision=$APP_REVISION"
            fi
        fi
    else
        pass "健康检查 UP"
    fi
else
    fail "健康检查不是 UP: $(printf '%s' "$HEALTH_BODY" | cut -c 1-180)"
fi

for page in /login /dashboard/home /dashboard/user /dashboard/createTask /dashboard/risk /dashboard/backups /dashboard/features /dashboard/rescue /dashboard/ops-terminal /dashboard/ops-audit /dashboard/sysCfg; do
    code="$(http_code "$page")"
    if [ "$code" = "200" ] && grep -q '<div id="app">' "$TMP_DIR/http-code-body"; then
        pass "页面可访问: $page"
    elif [ "$code" = "200" ]; then
        warn "页面 $page 返回 200，但未识别到 Vue 根节点"
    else
        fail "页面不可访问: $page HTTP $code"
    fi
done

TOKEN=""
if [ -n "${ADMIN_USERNAME:-}" ] && [ -n "${ADMIN_PASSWORD:-}" ]; then
    login_body="{\"account\":\"$(json_escape "$ADMIN_USERNAME")\",\"password\":\"$(json_escape "$ADMIN_PASSWORD")\"}"
    LOGIN_RSP="$(api_post /api/sys/login '' "$login_body")"
    TOKEN="$(printf '%s' "$LOGIN_RSP" | extract_json_string token)"
    if [ -n "$TOKEN" ]; then
        pass "Web 登录成功并取得 token"
    else
        fail "Web 登录失败或未返回 token: $(printf '%s' "$LOGIN_RSP" | cut -c 1-180)"
    fi
fi

if [ -n "$TOKEN" ]; then
    check_api_success "系统诊断" "$(api_get /api/v1/system/diagnostics "$TOKEN")"
    check_api_success "版本信息" "$(api_get /api/v1/system/version-info "$TOKEN")"
    check_api_success "首页概览" "$(api_get /api/sys/glance "$TOKEN")"
    check_api_success "配置分页" "$(api_post /api/oci/userPage "$TOKEN" '{"currentPage":1,"pageSize":5,"keyword":""}')"
    check_api_success "任务分页" "$(api_post /api/oci/createTaskPage "$TOKEN" '{"currentPage":1,"pageSize":5,"keyword":"","architecture":""}')"
    check_api_success "OCI 风险看板" "$(api_get /api/v1/oci/risk?maxConfigs=1 "$TOKEN")"
    check_api_success "运维主机列表" "$(api_get /api/ops/ssh/hosts "$TOKEN")"
    check_api_success "最近操作审计" "$(api_get /api/ops/audit/recent?limit=5 "$TOKEN")"
    check_api_success "最近服务日志" "$(api_get /api/v1/logs/recent?limit=20 "$TOKEN")"
    check_api_success "救援中心概览" "$(api_get /api/rescue/overview "$TOKEN")"
    check_api_success "本地备份列表" "$(api_get /api/v1/backups/local?limit=5 "$TOKEN")"
    check_api_success "定时备份方案" "$(api_get '/api/v1/backups/schedule-plan?cron=0%203%20*%20*%20*' "$TOKEN")"

    if [ -n "$OCI_CFG_ID" ]; then
        details_body="{\"ociCfgId\":\"$(json_escape "$OCI_CFG_ID")\"}"
        check_api_success "OCI 实时详情" "$(api_post /api/oci/details "$TOKEN" "$details_body")"
    else
        warn "未设置 OCI_CFG_ID，跳过 /api/oci/details 实时 OCI 详情检查"
    fi
else
    warn "未取得登录 token，已跳过需要认证的 API 检查"
fi

echo ""
echo "=== 应用日志和主机资源 ==="
APP_LOGS="$(docker logs --tail 160 king-detective 2>&1 || true)"
if echo "$APP_LOGS" | grep -Eiq 'APPLICATION FAILED TO START|OutOfMemoryError|BindException|Failed to start TG Bot'; then
    fail "应用最近日志包含启动级错误，请执行 docker logs -f king-detective 查看"
else
    pass "应用最近日志未发现启动级错误"
fi

if command -v df >/dev/null 2>&1; then
    disk_used="$(df -P "$APP_DIR" 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}')"
    if [ -n "$disk_used" ]; then
        if [ "$disk_used" -ge 90 ]; then
            warn "磁盘使用率较高: ${disk_used}%"
        else
            pass "磁盘使用率正常: ${disk_used}%"
        fi
    fi
fi

if command -v free >/dev/null 2>&1; then
    mem_free="$(free -m 2>/dev/null | awk '/Mem:/ {print $7}')"
    if [ -n "$mem_free" ]; then
        if [ "$mem_free" -lt 80 ]; then
            warn "可用内存偏低: ${mem_free}MB，低配 VPS 首次启动可能较慢"
        else
            pass "可用内存: ${mem_free}MB"
        fi
    fi
fi

echo ""
echo "=== 结果汇总 ==="
printf 'PASS: %s, WARN: %s, FAIL: %s\n' "$PASS_COUNT" "$WARN_COUNT" "$FAIL_COUNT"
echo "详细的真实 OCI 操作验收清单见 docs/DEPLOYMENT_SMOKE_TEST.md"

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
exit 0
