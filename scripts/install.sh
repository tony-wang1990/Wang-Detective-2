#!/bin/bash

# Keep this script with LF line endings. GitHub raw executes it directly on Linux.
set -u

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

DEFAULT_JAVA_TOOL_OPTIONS="-Xms96m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Dspring.jmx.enabled=false"

echo "=== King-Detective 安装脚本 ==="
echo "步骤 1: 检查环境..."

command -v wget >/dev/null 2>&1 || { echo "错误: 未安装 wget"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "错误: 未安装 curl"; exit 1; }

if ! command -v docker >/dev/null 2>&1; then
    echo "未检测到 Docker，正在尝试自动安装..."
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        if [[ "$ID" == "ubuntu" || "$ID" == "debian" || "$ID" == "centos" || "$ID" == "ol" ]]; then
            curl -fsSL https://get.docker.com | bash
            systemctl enable docker
            systemctl start docker
        else
            echo "错误: 不支持的操作系统 $ID，请手动安装 Docker"
            exit 1
        fi
    else
        echo "错误: 无法检测操作系统版本，请手动安装 Docker"
        exit 1
    fi
else
    echo "  - Docker 已安装"
fi

if ! command -v docker-compose >/dev/null 2>&1 && ! docker compose version >/dev/null 2>&1; then
    echo "未检测到 Docker Compose，正在尝试安装..."
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        if [[ "$ID" == "ubuntu" || "$ID" == "debian" ]]; then
            apt-get update && apt-get install -y docker-compose-plugin
        elif [[ "$ID" == "centos" || "$ID" == "ol" ]]; then
            yum install -y docker-compose-plugin
        fi
    fi

    if ! docker compose version >/dev/null 2>&1; then
        ARCH=$(uname -m)
        if [[ "$ARCH" == "aarch64" ]]; then
            curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-aarch64" -o /usr/local/bin/docker-compose
        else
            curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        fi
        chmod +x /usr/local/bin/docker-compose
    fi
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "错误: Docker 安装失败"
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    echo "  - Docker Compose Plugin 已安装"
    compose() { docker compose "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
    echo "  - docker-compose 已安装"
    compose() { docker-compose "$@"; }
else
    echo "错误: Docker Compose 安装失败"
    exit 1
fi

echo "步骤 2: 创建目录..."
mkdir -p /app/king-detective/data /app/king-detective/keys /app/king-detective/logs /app/king-detective/runtime /app/king-detective/scripts /app/king-detective/backups || {
    echo "错误: 无法创建目录"
    exit 1
}
cd /app/king-detective || {
    echo "错误: 无法进入目录 /app/king-detective"
    exit 1
}

echo "步骤 3: 下载配置文件..."

if [ ! -f "docker-compose.yml" ]; then
    wget -q https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/docker-compose.yml || {
        echo "错误: 下载 docker-compose.yml 失败"
        exit 1
    }
    echo "  - docker-compose.yml 下载成功"
else
    echo "  - docker-compose.yml 已存在，跳过下载"
fi

if grep -q "king-detective-websockify\\|ghcr.io/tony-wang1990/king-detective:main\\|start_period: 45s\\|profiles:.*watcher" docker-compose.yml \
    || ! grep -q "JAVA_TOOL_OPTIONS" docker-compose.yml \
    || ! grep -q "king-detective-watcher" docker-compose.yml \
    || ! grep -Fq 'image: ${KING_DETECTIVE_IMAGE:-ghcr.io/tony-wang1990/wang-detective:main}' docker-compose.yml \
    || ! grep -Fq './backups:/app/king-detective/backups' docker-compose.yml \
    || ! grep -Fq './scripts:/app/king-detective/scripts:ro' docker-compose.yml \
    || ! grep -Fq -- '-Dfile.encoding=UTF-8' docker-compose.yml; then
    backup_file="docker-compose.yml.bak.$(date +%Y%m%d%H%M%S)"
    cp docker-compose.yml "$backup_file"
    wget -q -O docker-compose.yml https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/docker-compose.yml || {
        echo "错误: 刷新 docker-compose.yml 失败"
        mv "$backup_file" docker-compose.yml
        exit 1
    }
    echo "  - 检测到旧版 docker-compose.yml，已备份为 $backup_file 并刷新为新版"
fi

if [ ! -f "application.yml" ]; then
    wget -q https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/src/main/resources/application.yml || {
        echo "错误: 下载 application.yml 失败"
        exit 1
    }
    echo "  - application.yml 下载成功"
else
    echo "  - application.yml 已存在，保留现有配置"
fi

if grep -q "address: '::'\\|^  port: 9527$" application.yml; then
    app_backup_file="application.yml.bak.$(date +%Y%m%d%H%M%S)"
    cp application.yml "$app_backup_file"
    sed -i "s|^  port: 9527$|  port: \${SERVER_PORT:9527}|" application.yml
    sed -i "s|address: '::'.*|address: \${SERVER_ADDRESS:0.0.0.0}|" application.yml
    echo "  - 已备份旧 application.yml 为 $app_backup_file，并切换为 IPv4 默认监听"
fi

if [ ! -f ".env" ]; then
    ADMIN_USERNAME="${ADMIN_USERNAME:-${WEB_ACCOUNT:-admin}}"
    ADMIN_PASSWORD="${ADMIN_PASSWORD:-${WEB_PASSWORD:-admin123456}}"
    OPS_SSH_SECRET_KEY="${OPS_SSH_SECRET_KEY:-$ADMIN_PASSWORD}"
    TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-${BOT_TOKEN:-}}"
    TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-${TG_CHAT_ID:-}}"
    TELEGRAM_BOT_CHAT_ID="${TELEGRAM_BOT_CHAT_ID:-$TELEGRAM_CHAT_ID}"
    JAVA_TOOL_OPTIONS_VALUE="${JAVA_TOOL_OPTIONS:-$DEFAULT_JAVA_TOOL_OPTIONS}"
    cat > .env <<EOF
ADMIN_USERNAME=${ADMIN_USERNAME}
ADMIN_PASSWORD=${ADMIN_PASSWORD}
OPS_SSH_SECRET_KEY=${OPS_SSH_SECRET_KEY}
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
TELEGRAM_CHAT_ID=${TELEGRAM_CHAT_ID}
TELEGRAM_BOT_CHAT_ID=${TELEGRAM_BOT_CHAT_ID}
TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME:-king_detective_bot}
OPENAI_API_KEY=${OPENAI_API_KEY:-}
OPENAI_BASE_URL=${OPENAI_BASE_URL:-https://api.siliconflow.cn}
CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-*}
SERVER_ADDRESS=${SERVER_ADDRESS:-0.0.0.0}
SERVER_PORT=${SERVER_PORT:-9527}
KING_DETECTIVE_GITHUB_REPOSITORY=${KING_DETECTIVE_GITHUB_REPOSITORY:-tony-wang1990/Wang-Detective}
KING_DETECTIVE_GITHUB_BRANCH=${KING_DETECTIVE_GITHUB_BRANCH:-main}
KING_DETECTIVE_IMAGE=${KING_DETECTIVE_IMAGE:-ghcr.io/tony-wang1990/wang-detective:main}
JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS_VALUE}
EOF
    chmod 600 .env
    echo "  - .env 已生成"
    if [ "$ADMIN_PASSWORD" = "admin123456" ]; then
        echo "  - 警告: 当前仍使用默认密码，建议尽快修改 ADMIN_PASSWORD"
    fi
else
    echo "  - .env 已存在，保留现有环境配置"
fi

ensure_env() {
    key="$1"
    value="$2"
    if ! grep -q "^${key}=" .env; then
        printf '%s=%s\n' "$key" "$value" >> .env
        echo "  - .env 已补充 ${key}"
    fi
}

append_env_word() {
    key="$1"
    word="$2"
    tmp_env=".env.tmp.$$"
    awk -v key="$key" -v word="$word" '
        BEGIN { found = 0 }
        index($0, key "=") == 1 {
            found = 1
            value = substr($0, length(key) + 2)
            if (index(" " value " ", " " word " ") == 0) {
                $0 = $0 " " word
                changed = 1
            }
        }
        { print }
        END {
            if (!found) {
                print key "=" word
                changed = 1
            }
        }
    ' .env > "$tmp_env" && mv "$tmp_env" .env
}

current_admin_password="$(grep -E '^ADMIN_PASSWORD=' .env | tail -n1 | cut -d= -f2-)"
current_admin_password="${current_admin_password:-admin123456}"
current_tg_chat_id="$(grep -E '^TELEGRAM_BOT_CHAT_ID=' .env | tail -n1 | cut -d= -f2-)"
current_tg_chat_id="${current_tg_chat_id:-$(grep -E '^TELEGRAM_CHAT_ID=' .env | tail -n1 | cut -d= -f2-)}"
current_tg_chat_id="${current_tg_chat_id:-$(grep -E '^TG_CHAT_ID=' .env | tail -n1 | cut -d= -f2-)}"

ensure_env "OPS_SSH_SECRET_KEY" "$current_admin_password"
ensure_env "TELEGRAM_CHAT_ID" "$current_tg_chat_id"
ensure_env "TELEGRAM_BOT_CHAT_ID" "$current_tg_chat_id"
ensure_env "SERVER_ADDRESS" "0.0.0.0"
ensure_env "SERVER_PORT" "9527"
ensure_env "KING_DETECTIVE_GITHUB_REPOSITORY" "tony-wang1990/Wang-Detective"
ensure_env "KING_DETECTIVE_GITHUB_BRANCH" "main"
ensure_env "KING_DETECTIVE_IMAGE" "ghcr.io/tony-wang1990/wang-detective:main"
ensure_env "JAVA_TOOL_OPTIONS" "$DEFAULT_JAVA_TOOL_OPTIONS"
append_env_word "JAVA_TOOL_OPTIONS" "-Dfile.encoding=UTF-8"
append_env_word "JAVA_TOOL_OPTIONS" "-Dstdout.encoding=UTF-8"
append_env_word "JAVA_TOOL_OPTIONS" "-Dstderr.encoding=UTF-8"
chmod 600 .env

echo "步骤 3.1: 同步运维脚本..."
SCRIPT_BASE_URL="https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts"
download_script() {
    script_name="$1"
    target="scripts/${script_name}"
    temp_target="${target}.tmp.$$"
    url="${SCRIPT_BASE_URL}/${script_name}"

    if [ -d "$target" ]; then
        backup_target="${target}.dir.bak.$(date +%Y%m%d%H%M%S)"
        mv "$target" "$backup_target" || {
            echo "错误: 备份目录 ${target} 失败"
            exit 1
        }
        echo "  - 检测到 ${target} 是目录，已备份为 ${backup_target}"
    elif [ -e "$target" ] && [ ! -f "$target" ]; then
        backup_target="${target}.bak.$(date +%Y%m%d%H%M%S)"
        mv "$target" "$backup_target" || {
            echo "错误: 备份异常路径 ${target} 失败"
            exit 1
        }
        echo "  - 检测到 ${target} 不是普通文件，已备份为 ${backup_target}"
    fi

    rm -f "$temp_target"
    wget -q -O "$temp_target" "$url" || {
        rm -f "$temp_target"
        echo "错误: 下载 ${target} 失败"
        exit 1
    }
    mv "$temp_target" "$target" || {
        rm -f "$temp_target"
        echo "错误: 写入 ${target} 失败"
        exit 1
    }
}

for script_name in \
    watcher.sh \
    server-smoke-test.sh \
    backup.sh \
    restore.sh \
    update.sh \
    rollback.sh \
    support-bundle.sh \
    maintenance.sh \
    setup-backup-cron.sh \
    verify-release.sh \
    remote-smoke-test.sh \
    remote-smoke-test.mjs
do
    download_script "$script_name"
done
chmod +x scripts/*.sh
echo "  - 运维脚本已同步"

echo "步骤 4: 拉取最新镜像..."
compose pull king-detective watcher || {
    echo "错误: 拉取核心镜像或 watcher 镜像失败"
    exit 1
}

echo "步骤 5: 启动服务..."
for container_id in $(docker ps -aq --filter "name=king-detective"); do
    container_name="$(docker inspect --format '{{.Name}}' "$container_id" 2>/dev/null | sed 's#^/##')"
    case "$container_name" in
        king-detective|*_king-detective)
            docker rm -f "$container_id" >/dev/null 2>&1 || true
            ;;
    esac
done

compose up -d king-detective watcher || {
    echo "错误: 启动服务失败"
    exit 1
}

echo "步骤 6: 等待服务就绪..."
HEALTH_URL="http://127.0.0.1:9527/actuator/health"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-900}"
WAIT_STARTED_AT="$(date +%s)"
while true; do
    HEALTH_BODY="$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null || true)"
    if echo "$HEALTH_BODY" | grep -q '"status":"UP"'; then
        echo "  - 服务已就绪 health=UP"
        break
    fi

    NOW="$(date +%s)"
    ELAPSED=$((NOW - WAIT_STARTED_AT))
    if [ "$ELAPSED" -ge "$HEALTH_TIMEOUT_SECONDS" ]; then
        echo "错误: 服务在 ${HEALTH_TIMEOUT_SECONDS} 秒内未就绪"
        echo "----- docker ps -----"
        docker ps --filter "name=king-detective"
        echo "----- docker logs --tail 120 king-detective -----"
        docker logs --tail 120 king-detective || true
        exit 1
    fi

    CONTAINER_STATUS="$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' king-detective 2>/dev/null || true)"
    echo "  - 服务启动中... ${ELAPSED}s/${HEALTH_TIMEOUT_SECONDS}s ${CONTAINER_STATUS}"
    sleep 15
done

PUBLIC_IP="$(curl -fsS --max-time 8 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')"

echo ""
echo "=== 安装完成！ ==="
echo "访问地址: http://${PUBLIC_IP}:9527"
echo "账号信息: 请查看 /app/king-detective/.env 中的 ADMIN_USERNAME / ADMIN_PASSWORD"
echo "服务器本机健康检查: ${HEALTH_URL}"
echo "注意: 127.0.0.1 只能在服务器 SSH 内访问，电脑浏览器请使用上面的公网 IP 地址"
