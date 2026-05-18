#!/bin/bash

###############################################################################
# King-Detective 一键部署/更新脚本
# Version: 2.0.0
# Date: 2026-01-04
###############################################################################

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Docker是否安装
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装，请先安装Docker"
        exit 1
    fi
    log_success "Docker已安装"
}

# 检查Docker Compose是否安装
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装，请先安装Docker Compose"
        exit 1
    fi
    log_success "Docker Compose已安装"
}

# 显示欢迎信息
show_welcome() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║     King-Detective 一键部署脚本 v2.0.0                    ║"
    echo "║     Author: Tony Wang                                      ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
}

# 检查是否为首次部署
check_deployment_type() {
    if [ -f "docker-compose.yml" ] && docker ps | grep -q "king-detective"; then
        DEPLOYMENT_TYPE="update"
        log_info "检测到现有部署，将执行更新操作"
    else
        DEPLOYMENT_TYPE="new"
        log_info "未检测到现有部署，将执行全新安装"
    fi
}

# 拉取最新镜像
pull_latest_image() {
    log_info "正在拉取最新Docker镜像..."
    docker pull ghcr.io/tony-wang1990/wang-detective:main
    log_success "镜像拉取完成"
}

# 停止现有容器
stop_containers() {
    if [ "$DEPLOYMENT_TYPE" = "update" ]; then
        log_info "停止现有容器..."
        docker-compose down
        log_success "容器已停止"
    fi
}

# 启动服务
start_services() {
    log_info "启动服务..."
    docker-compose up -d
    log_success "服务已启动"
}

# 等待服务就绪
wait_for_service() {
    log_info "等待服务启动..."
    sleep 10
    
    # 检查健康状态（最多等待60秒）
    for i in {1..12}; do
        if curl -s http://localhost:9527/actuator/health | grep -q "UP"; then
            log_success "服务已就绪"
            return 0
        fi
        log_info "等待服务启动...($i/12)"
        sleep 5
    done
    
    log_warning "服务启动超时，请检查日志"
    return 1
}

# 显示日志
show_logs() {
    log_info "显示启动日志（按Ctrl+C退出）..."
    docker logs -f king-detective
}

# 配置TG频道（交互式）
configure_tg_channel() {
    echo ""
    log_info "TG频道配置（可选）"
    echo ""
    read -p "是否配置TG频道广播? (y/n): " CONFIG_TG
    
    if [ "$CONFIG_TG" = "y" ] || [ "$CONFIG_TG" = "Y" ]; then
        read -p "请输入Bot Token: " BOT_TOKEN
        read -p "请输入频道ID (例如 @your_channel): " CHANNEL_ID
        
        if [ -n "$BOT_TOKEN" ] && [ -n "$CHANNEL_ID" ]; then
            TG_URL="https://api.telegram.org/bot${BOT_TOKEN}/sendMessage?chat_id=${CHANNEL_ID}"
            
            log_info "配置TG频道到数据库..."
            docker exec -i $(docker ps -qf "name=mysql") mysql -uroot -p${MYSQL_ROOT_PASSWORD} king_detective <<EOF
INSERT INTO oci_kv (code, value) 
VALUES ('Y114', '${TG_URL}')
ON DUPLICATE KEY UPDATE value = VALUES(value);
EOF
            log_success "TG频道配置完成"
        else
            log_warning "跳过TG频道配置"
        fi
    else
        log_info "跳过TG频道配置"
    fi
}

# 显示部署信息
show_deployment_info() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║                     部署完成                               ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    log_success "King-Detective已成功部署！"
    echo ""
    echo "📊 服务信息："
    echo "   - 应用地址: http://localhost:9527"
    echo "   - 健康检查: http://localhost:9527/actuator/health"
    echo ""
    echo "🔧 管理命令："
    echo "   - 查看日志: docker logs -f king-detective"
    echo "   - 重启服务: docker-compose restart"
    echo "   - 停止服务: docker-compose down"
    echo ""
    echo "📚 更多信息："
    echo "   - GitHub: https://github.com/tony-wang1990/Wang-Detective"
    echo "   - 文档: 查看 README.md"
    echo ""
}

# 错误处理
handle_error() {
    log_error "部署过程中发生错误！"
    echo ""
    echo "请检查："
    echo "1. Docker和Docker Compose是否正确安装"
    echo "2. docker-compose.yml配置是否正确"
    echo "3. 数据库连接是否正常"
    echo ""
    echo "查看详细日志："
    echo "docker logs king-detective"
    exit 1
}

# 主函数
main() {
    # 设置错误处理
    trap handle_error ERR
    
    # 显示欢迎信息
    show_welcome
    
    # 检查环境
    check_docker
    check_docker_compose
    
    # 检查部署类型
    check_deployment_type
    
    # 拉取最新镜像
    pull_latest_image
    
    # 停止现有容器（如果是更新）
    stop_containers
    
    # 启动服务
    start_services
    
    # 等待服务就绪
    if wait_for_service; then
        # TG频道配置（可选）
        if [ "$DEPLOYMENT_TYPE" = "new" ]; then
            configure_tg_channel
        fi
        
        # 显示部署信息
        show_deployment_info
        
        # 询问是否查看日志
        echo ""
        read -p "是否查看实时日志? (y/n): " SHOW_LOGS
        if [ "$SHOW_LOGS" = "y" ] || [ "$SHOW_LOGS" = "Y" ]; then
            show_logs
        fi
    else
        log_warning "请手动检查服务状态"
    fi
}

# 执行主函数
main
