#!/bin/bash
# 安全修复快速执行脚本
# 执行前请先在BotFather撤销Token并获取新Token

set -e

echo "🔐 King-Detective 安全修复脚本"
echo "================================"
echo ""

# 检查是否已撤销Token
read -p "❓ 是否已在BotFather撤销Token并获取新Token? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ 请先在 @BotFather 撤销Token："
    echo "   1. 发送 /mybots"
    echo "   2. 选择Bot → API Token → Revoke current token"
    exit 1
fi

# 获取新Token
echo ""
echo "📝 请输入新的Telegram Bot Token:"
read -r NEW_TOKEN

if [ -z "$NEW_TOKEN" ]; then
    echo "❌ Token不能为空"
    exit 1
fi

# 创建.env文件
echo ""
echo "📝 创建 .env 文件..."
cat > .env <<EOF
# Telegram Bot Configuration
TELEGRAM_BOT_TOKEN=$NEW_TOKEN

# Web Admin Credentials
ADMIN_USERNAME=${ADMIN_USERNAME:-${WEB_ACCOUNT:-admin}}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-${WEB_PASSWORD:-admin123456}}

EOF

echo "✅ .env 文件已创建"

# 验证.gitignore
if ! grep -q ".env" .gitignore 2>/dev/null; then
    echo "⚠️  添加 .env 到 .gitignore"
    echo ".env" >> .gitignore
fi

# 提交更改
echo ""
read -p "❓ 是否提交更改到Git? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    git add .gitignore .env.example src/main/resources/application.yml
    git commit -m "security: use environment variables for sensitive data"
    echo "✅ 更改已提交"

    read -p "❓ 是否推送到远程仓库? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git push
        echo "✅ 已推送到远程仓库"
    fi
fi

# 重启服务
echo ""
read -p "❓ 是否重启Docker服务? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔧 重启服务..."
    docker-compose down
    docker-compose up -d
    echo "✅ 服务已重启"

    echo ""
    echo "📋 查看日志:"
    docker-compose logs -f --tail=50
fi

echo ""
echo "✅ 安全修复完成！"
echo ""
echo "🔍 下一步："
echo "1. 测试Bot是否正常 (发送 /start)"
echo "2. 在GitHub关闭安全警报"
echo "3. 检查是否需要清理Git历史"
