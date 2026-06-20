# 安全修复快速执行脚本 (Windows PowerShell)
# 执行前请先在BotFather撤销旧Token并获取新Token

Write-Host "🔒 King-Detective 安全修复脚本" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否已撤销Token
$confirmed = Read-Host "❓ 是否已在BotFather撤销旧Token并获取新Token? (y/n)"
if ($confirmed -ne 'y') {
    Write-Host "❌ 请先在 @BotFather 撤销旧Token！" -ForegroundColor Red
    Write-Host "   1. 发送 /mybots"
    Write-Host "   2. 选择Bot → API Token → Revoke current token"
    exit 1
}

# 获取新Token
Write-Host ""
Write-Host "📝 请输入新的Telegram Bot Token:"
$NEW_TOKEN = Read-Host

if ([string]::IsNullOrWhiteSpace($NEW_TOKEN)) {
    Write-Host "❌ Token不能为空" -ForegroundColor Red
    exit 1
}

# 获取其他配置
Write-Host ""
Write-Host "📝 请输入Web账号 (默认: admin):"
$WEB_ACCOUNT = Read-Host
if ([string]::IsNullOrWhiteSpace($WEB_ACCOUNT)) {
    $WEB_ACCOUNT = "admin"
}

Write-Host "📝 请输入Web密码 (默认: admin123456):"
$WEB_PASSWORD = Read-Host
if ([string]::IsNullOrWhiteSpace($WEB_PASSWORD)) {
    $WEB_PASSWORD = "admin123456"
}

# 创建.env文件
Write-Host ""
Write-Host "📝 创建 .env 文件..." -ForegroundColor Yellow

$envContent = @"
# Telegram Bot Configuration
TELEGRAM_BOT_TOKEN=$NEW_TOKEN

# Web Admin Credentials
ADMIN_USERNAME=$WEB_ACCOUNT
ADMIN_PASSWORD=$WEB_PASSWORD

"@

Set-Content -Path ".env" -Value $envContent -Encoding UTF8
Write-Host "✅ .env 文件已创建" -ForegroundColor Green

# 验证.gitignore
if (Test-Path ".gitignore") {
    $gitignoreContent = Get-Content ".gitignore" -Raw
    if ($gitignoreContent -notmatch "\.env") {
        Write-Host "⚠️  添加 .env 到 .gitignore" -ForegroundColor Yellow
        Add-Content -Path ".gitignore" -Value "`n.env"
    }
}

# 提交更改
Write-Host ""
$commitChanges = Read-Host "❓ 是否提交更改到Git? (y/n)"
if ($commitChanges -eq 'y') {
    git add .gitignore .env.example src/main/resources/application.yml
    git commit -m "security: use environment variables for sensitive data"
    Write-Host "✅ 更改已提交" -ForegroundColor Green
    
    $pushChanges = Read-Host "❓ 是否推送到远程仓库? (y/n)"
    if ($pushChanges -eq 'y') {
        git push
        Write-Host "✅ 已推送到远程仓库" -ForegroundColor Green
    }
}

# 重启服务
Write-Host ""
$restartService = Read-Host "❓ 是否重启Docker服务? (y/n)"
if ($restartService -eq 'y') {
    Write-Host "🔄 重启服务..." -ForegroundColor Yellow
    docker-compose down
    docker-compose up -d
    Write-Host "✅ 服务已重启" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "📋 查看日志:"
    docker-compose logs -f --tail=50
}

Write-Host ""
Write-Host "✅ 安全修复完成！" -ForegroundColor Green
Write-Host ""
Write-Host "🔍 下一步："
Write-Host "1. 测试Bot是否正常 (发送 /start)"
Write-Host "2. 在GitHub关闭安全警报"
Write-Host "3. 检查是否需要清理Git历史"
