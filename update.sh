#!/bin/bash

# King-Detective 一键更新脚本
# 保留所有数据：账户、密码、私钥、配置

set -e

echo "========================================"
echo "   King-Detective 一键更新脚本"
echo "========================================"
echo ""

# 配置变量
CONTAINER_NAME="king-detective"
IMAGE_NAME="ghcr.io/tony-wang1990/wang-detective:main"
BASE_DIR="${KING_DETECTIVE_HOME:-/app/king-detective}"
DATA_DIR="$BASE_DIR/data"
KEYS_DIR="$BASE_DIR/keys"
LOGS_DIR="$BASE_DIR/logs"

# 从环境变量读取敏感配置（请在服务器上设置这些环境变量）
# 示例：export BOT_TOKEN="你的token"
#      export ADMIN_USERNAME="你的用户名"
#      export ADMIN_PASSWORD="你的密码"

TELEGRAM_TOKEN="${TELEGRAM_BOT_TOKEN:-${BOT_TOKEN:-}}"

if [ -z "$TELEGRAM_TOKEN" ]; then
    echo "❌ 错误: 未设置环境变量 TELEGRAM_BOT_TOKEN 或 BOT_TOKEN"
    echo ""
    echo "请先设置环境变量："
    echo "  export BOT_TOKEN=\"你的Telegram Bot Token\""
    echo "  export ADMIN_USERNAME=\"你的管理员用户名\""
    echo "  export ADMIN_PASSWORD=\"你的管理员密码\""
    echo ""
    echo "或者直接在命令行运行："
    echo "  BOT_TOKEN=\"xxx\" ADMIN_USERNAME=\"xxx\" ADMIN_PASSWORD=\"xxx\" bash update.sh"
    exit 1
fi

if [ -z "$ADMIN_USERNAME" ]; then
    echo "❌ 错误: 未设置环境变量 ADMIN_USERNAME"
    exit 1
fi

if [ -z "$ADMIN_PASSWORD" ]; then
    echo "❌ 错误: 未设置环境变量 ADMIN_PASSWORD"
    exit 1
fi

echo "📦 步骤 1/5: 检查数据目录..."
if [ ! -d "$DATA_DIR" ]; then
    echo "⚠️  数据目录不存在，创建: $DATA_DIR"
    mkdir -p "$DATA_DIR"
fi

if [ ! -d "$KEYS_DIR" ]; then
    echo "⚠️  私钥目录不存在，创建: $KEYS_DIR"
    mkdir -p "$KEYS_DIR"
fi

if [ ! -d "$LOGS_DIR" ]; then
    echo "⚠️  日志目录不存在，创建: $LOGS_DIR"
    mkdir -p "$LOGS_DIR"
fi

# 检查数据库文件
if [ -f "$DATA_DIR/king-detective.db" ]; then
    echo "✅ 数据库文件存在"
    # 备份数据库
    BACKUP_FILE="$DATA_DIR/king-detective.db.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$DATA_DIR/king-detective.db" "$BACKUP_FILE"
    echo "📋 已备份数据库到: $BACKUP_FILE"
else
    echo "⚠️  数据库文件不存在（首次安装正常）"
fi

echo ""
echo "🛑 步骤 2/5: 停止旧容器..."
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    docker stop "$CONTAINER_NAME" 2>/dev/null || true
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    echo "✅ 已停止并删除旧容器"
else
    echo "ℹ️  容器不存在（首次安装正常）"
fi

echo ""
echo "⬇️  步骤 3/5: 拉取最新镜像..."
docker pull "$IMAGE_NAME"

echo ""
echo "🚀 步骤 4/5: 启动新容器..."
docker run -d \
  --name "$CONTAINER_NAME" \
  -p 9527:9527 \
  -e ADMIN_USERNAME="$ADMIN_USERNAME" \
  -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  -e TELEGRAM_BOT_TOKEN="$TELEGRAM_TOKEN" \
  -v "$DATA_DIR:/app/king-detective/data" \
  -v "$KEYS_DIR:/app/king-detective/keys" \
  -v "$LOGS_DIR:/var/log" \
  --restart unless-stopped \
  "$IMAGE_NAME"

echo "✅ 容器已启动"

echo ""
echo "⏳ 步骤 5/5: 等待服务启动..."
sleep 5

echo ""
echo "📊 检查容器状态..."
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "✅ 容器运行中"
    echo ""
    echo "📋 最近日志："
    docker logs "$CONTAINER_NAME" --tail 10
else
    echo "❌ 容器未运行，查看错误日志："
    docker logs "$CONTAINER_NAME" --tail 30
    exit 1
fi

echo ""
echo "========================================"
echo "✅ 更新完成！"
echo "========================================"
echo ""
echo "📁 数据保留位置："
echo "   - 数据库: $DATA_DIR/king-detective.db"
echo "   - 私钥: $KEYS_DIR/"
echo ""
echo "🔗 访问地址: http://你的服务器IP:9527"
echo "🤖 Telegram Bot: @你的Bot用户名"
echo ""
echo "💡 常用命令："
echo "   查看日志: docker logs -f $CONTAINER_NAME"
echo "   重启容器: docker restart $CONTAINER_NAME"
echo "   停止容器: docker stop $CONTAINER_NAME"
echo ""
