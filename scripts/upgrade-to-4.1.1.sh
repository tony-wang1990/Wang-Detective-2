#!/bin/bash
#
# King-Detective v4.1.1 一键升级脚本
# 功能：安全升级到v4.1.1，保留所有配置和数据
# 作者：Antigravity AI
#

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_header() {
    echo ""
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"
    echo ""
}

# 检查是否在正确的目录
check_directory() {
    if [ ! -f "docker-compose.yml" ] || [ ! -f "pom.xml" ]; then
        print_error "请在king-detective项目根目录下执行此脚本！"
        print_info "当前目录: $(pwd)"
        exit 1
    fi
}

# 检查docker compose命令
check_docker_compose() {
    if command -v docker &> /dev/null && docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
        print_success "检测到 docker compose V2"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
        print_warning "检测到 docker-compose V1，建议升级到V2"
    else
        print_error "未找到docker compose命令！"
        exit 1
    fi
}

# 主函数
main() {
    print_header "🚀 King-Detective v4.1.1 升级脚本"

    print_info "此脚本将安全升级到v4.1.1版本"
    print_info "所有配置和数据将被保留"
    echo ""

    # 确认继续
    read -p "是否继续升级？(y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "升级已取消"
        exit 0
    fi

    # Step 0: 环境检查
    print_header "[0/8] 环境检查"
    check_directory
    check_docker_compose

    # Step 1: 备份配置文件
    print_header "[1/8] 备份配置文件"
    BACKUP_SUFFIX=$(date +%Y%m%d_%H%M%S)

    if [ -f "docker-compose.yml" ]; then
        cp docker-compose.yml docker-compose.yml.backup.$BACKUP_SUFFIX
        print_success "已备份 docker-compose.yml"
    fi

    if [ -f "application.yml" ]; then
        cp application.yml application.yml.backup.$BACKUP_SUFFIX
        print_success "已备份 application.yml"
    fi

    if [ -f "king-detective.db" ]; then
        cp king-detective.db king-detective.db.backup.$BACKUP_SUFFIX
        print_success "已备份 king-detective.db"
    fi

    print_info "备份文件后缀: $BACKUP_SUFFIX"

    # Step 2: 拉取最新代码
    print_header "[2/8] 拉取v4.1.1代码"

    # 保存本地修改
    if ! git diff-index --quiet HEAD --; then
        print_warning "检测到本地修改，正在暂存..."
        git stash
        STASHED=true
    else
        STASHED=false
    fi

    # 拉取代码
    git fetch origin
    git checkout main
    git pull origin main

    print_success "代码已更新到最新版本"

    # Step 3: 检查配置文件
    print_header "[3/8] 检查配置文件"

    if [ ! -f "application.yml" ]; then
        print_warning "application.yml不存在，从备份恢复..."
        if [ -f "application.yml.backup.$BACKUP_SUFFIX" ]; then
            cp application.yml.backup.$BACKUP_SUFFIX application.yml
            print_success "已恢复 application.yml"
        else
            print_error "未找到备份文件！请手动检查配置"
        fi
    else
        print_success "application.yml 存在"
    fi

    # 验证配置内容
    if grep -q "account:" application.yml && grep -q "password:" application.yml; then
        print_success "配置文件包含账户信息"
    else
        print_warning "配置文件可能不完整，请检查！"
    fi

    # Step 4: 修复trigger文件
    print_header "[4/8] 修复trigger文件"

    if [ -d "update_version_trigger.flag" ]; then
        print_warning "trigger是目录，正在修复..."
        rm -rf update_version_trigger.flag
    fi

    touch update_version_trigger.flag
    chmod 666 update_version_trigger.flag
    print_success "trigger文件已修复"

    # Step 5: 停止旧watcher
    print_header "[5/8] 停止旧watcher容器"

    $COMPOSE_CMD stop watcher 2>/dev/null || true
    $COMPOSE_CMD rm -f watcher 2>/dev/null || true

    # 删除旧镜像
    OLD_IMAGE=$(docker images | grep "oci-helper-watcher" | awk '{print $3}' | head -n 1)
    if [ -n "$OLD_IMAGE" ]; then
        print_info "删除旧watcher镜像..."
        docker rmi -f $OLD_IMAGE 2>/dev/null || true
    fi

    print_success "旧watcher已停止"

    # Step 6: 拉取新镜像
    print_header "[6/8] 拉取最新Docker镜像"

    print_info "正在拉取 ghcr.io/tony-wang1990/wang-detective-2:main..."
    $COMPOSE_CMD pull king-detective

    print_info "正在拉取 alpine:latest (用于watcher)..."
    docker pull alpine:latest

    print_success "镜像拉取完成"

    # Step 7: 重启服务
    print_header "[7/8] 重启所有服务"

    $COMPOSE_CMD up -d

    print_info "等待容器启动..."
    sleep 8

    # Step 8: 验证部署
    print_header "[8/8] 验证部署"

    echo ""
    print_info "容器状态："
    $COMPOSE_CMD ps

    echo ""
    print_info "Watcher日志（最近5行）："
    docker logs king-detective-watcher --tail 5 2>&1 || print_warning "Watcher日志暂时无法获取"

    # 最终总结
    print_header "✅ 升级完成！"

    echo ""
    print_success "King-Detective已升级到v4.1.1"
    echo ""

    print_info "📋 验证清单："
    echo "  1. 在Telegram Bot上发送 /start 查看新的4列布局"
    echo "  2. 登录Web面板验证配置未丢失"
    echo "  3. 点击'版本信息'查看当前版本"
    echo "  4. 测试'更新到最新版本'按钮（自动更新功能）"
    echo ""

    print_info "📦 备份文件位置："
    echo "  - docker-compose.yml.backup.$BACKUP_SUFFIX"
    echo "  - application.yml.backup.$BACKUP_SUFFIX"
    echo "  - king-detective.db.backup.$BACKUP_SUFFIX"
    echo ""

    print_info "📄 查看日志："
    echo "  docker logs -f king-detective-watcher  # Watcher日志"
    echo "  docker logs -f king-detective          # 主服务日志"
    echo ""

    print_info "🔧 以后的更新："
    echo "  从v4.1.1开始，可以直接在Bot上一键更新，无需手动操作"
    echo ""

    # 恢复暂存的修改
    if [ "$STASHED" = true ]; then
        print_warning "检测到之前暂存的修改，是否恢复？(y/n)"
        read -p "> " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git stash pop
            print_success "已恢复本地修改"
        fi
    fi

    print_success "升级脚本执行完毕！"
}

# 执行主函数
main

exit 0
