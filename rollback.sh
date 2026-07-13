#!/bin/bash

# King-Detective 回滚脚本
# 用于在更新失败时恢复旧版本

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

CONTAINER_NAME="king-detective"
OLD_IMAGE="ghcr.io/tony-wang1990/wang-detective-2:main"
PORT="9527"

echo -e "${YELLOW}================================${NC}"
echo -e "${YELLOW}King-Detective 回滚到旧版本${NC}"
echo -e "${YELLOW}================================${NC}"
echo ""

# 停止新容器
echo -e "${YELLOW}[1/4]${NC} 停止新容器..."
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true
echo -e "${GREEN}✓ 新容器已删除${NC}"

# 查找备份文件
echo -e "${YELLOW}[2/4]${NC} 查找最新备份..."
BACKUP_FILE=$(ls -t king-detective.db.backup-* 2>/dev/null | head -1)
if [ -z "$BACKUP_FILE" ]; then
    BACKUP_FILE=$(docker exec $CONTAINER_NAME ls -t /app/king-detective.db.backup-* 2>/dev/null | head -1)
fi

if [ -n "$BACKUP_FILE" ]; then
    echo -e "${GREEN}✓ 找到备份: $BACKUP_FILE${NC}"
else
    echo -e "${RED}警告: 未找到备份文件${NC}"
fi

# 拉取旧镜像
echo -e "${YELLOW}[3/4]${NC} 拉取旧版本镜像..."
docker pull $OLD_IMAGE
echo -e "${GREEN}✓ 旧镜像拉取完成${NC}"

# 启动旧容器
echo -e "${YELLOW}[4/4]${NC} 启动旧版本容器..."

DATA_DIR=""
if [ -d "/root/king-detective/data" ]; then
    DATA_DIR="/root/king-detective/data"
elif [ -d "./data" ]; then
    DATA_DIR="$(pwd)/data"
else
    DATA_DIR="$(pwd)/data"
fi

docker run -d \
  --name $CONTAINER_NAME \
  --restart unless-stopped \
  -p $PORT:$PORT \
  -v $DATA_DIR:/app \
  $OLD_IMAGE

echo -e "${GREEN}✓ 旧版本已恢复${NC}"

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}回滚完成!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "查看日志: docker logs $CONTAINER_NAME"
echo ""
