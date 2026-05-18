# 📦 King-Detective 部署指南

## 系统要求

### 最低配置
- **CPU**: 1核
- **内存**: 512MB
- **存储**: 2GB
- **OS**: Linux (推荐 Ubuntu 20.04+)
- **Docker**: 20.10+
- **Docker Compose**: 1.29+

### 推荐配置
- **CPU**: 2核
- **内存**: 1GB
- **存储**: 5GB
- **OS**: Ubuntu 22.04 LTS
- **Docker**: 最新版
- **Docker Compose**: 2.x

---

## 📋 部署前准备

### 1. 获取Telegram Bot Token

1. 打开 [@BotFather](https://t.me/BotFather)
2. 发送 `/newbot` 创建Bot
3. 设置Bot名称和username
4. **复制Token**（格式：`123456:ABC-DEF...`）
5. **不要泄露Token！**

### 2. 准备OCI凭据

- Oracle Cloud账户
- API密钥对（私钥PEM文件）
- Config文件

### 3. 创建部署目录

```bash
mkdir -p /opt/king-detective
cd /opt/king-detective
```

---

## 🚀 快速部署（Docker）

### 步骤1: 克隆代码

```bash
git clone https://github.com/tony-wang1990/Wang-Detective.git
cd Wang-Detective
```

### 步骤2: 配置环境变量

```bash
# 创建.env文件
cat > .env << 'EOF'
# Telegram Bot
TELEGRAM_BOT_TOKEN=你的Bot Token
TELEGRAM_BOT_USERNAME=你的Bot Username

# Web Admin
ADMIN_USERNAME=admin
ADMIN_PASSWORD=强密码请改掉
OPS_SSH_SECRET_KEY=请设置为稳定随机密钥

# OpenAI (可选)
OPENAI_API_KEY=

# Database
DATABASE_PATH=/app/data/king-detective.db
EOF
```

⚠️ **重要**: `.env`文件包含敏感信息，确保已在`.gitignore`中！

### 步骤3: 配置docker-compose.yml

```yaml
version: '3.8'

services:
  king-detective:
    build: .
    container_name: king-detective
    restart: unless-stopped
    ports:
      - "9527:9527"  # Web端口
    environment:
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME}
      - ADMIN_USERNAME=${ADMIN_USERNAME}
      - ADMIN_PASSWORD=${ADMIN_PASSWORD}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    volumes:
      - ./data:/app/data        # 数据库
      - ./keys:/app/keys        # OCI密钥
      - ./logs:/app/logs        # 日志
    networks:
      - king-detective-net

networks:
  king-detective-net:
    driver: bridge
```

### 步骤4: 初始化数据库

```bash
# 创建数据目录
mkdir -p data

# 执行SQL脚本（可选，首次运行会自动创建）
sqlite3 data/king-detective.db < docs/security_tables.sql
```

### 步骤5: 构建并启动

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

---

## 🔧 验证部署

### 1. 检查服务状态

```bash
# 查看容器
docker ps | grep king-detective

# 查看日志
docker logs -f king-detective
```

应该看到：
```
✅ Telegram Bot started successfully
✅ Web server started on port 9527
✅ Database initialized
```

### 2. 测试Telegram Bot

1. 在Telegram中搜索你的Bot
2. 发送 `/start`
3. 应该收到主菜单

### 3. 测试Web界面

```bash
curl http://localhost:9527/actuator/health
```

应该返回：
```json
{"status": "UP"}
```

### 4. 测试登录

```bash
curl -X POST http://localhost:9527/api/sys/login \
  -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"your_password"}'
```

---

## 🔒 安全配置

### 1. 启用防火墙

```bash
# UFW (Ubuntu)
sudo ufw allow 9527/tcp
sudo ufw enable
```

### 2. 配置反向代理（推荐）

**Nginx配置**:
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:9527;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 3. SSL证书（可选）

```bash
# 使用Certbot
sudo certbot --nginx -d your-domain.com
```

---

## 📊 监控和维护

### 查看日志

```bash
# 实时日志
docker-compose logs -f

# 最近100行
docker-compose logs --tail=100

# 只看错误
docker-compose logs | grep ERROR
```

### 备份数据

```bash
# 备份数据库
cp data/king-detective.db backups/king-detective-$(date +%Y%m%d).db

# 备份配置
tar -czf backups/config-$(date +%Y%m%d).tar.gz .env data/ keys/
```

### 更新版本

```bash
# 拉取最新代码
git pull

# 重新构建
docker-compose build

# 重启服务
docker-compose down
docker-compose up -d
```

---

## 🐛 故障排查

### Bot无响应

1. 检查Token是否正确
```bash
docker logs king-detective | grep "Bot Token"
```

2. 检查网络连接
```bash
docker exec king-detective ping api.telegram.org
```

### 数据库错误

1. 检查权限
```bash
ls -la data/
# 应该可写
```

2. 重新初始化
```bash
rm data/king-detective.db
docker-compose restart
```

### Web端无法访问

1. 检查端口
```bash
netstat -tunlp | grep 9527
```

2. 检查防火墙
```bash
sudo ufw status
```

---

## 📝 生产环境建议

### 1. 性能优化

```yaml
# docker-compose.yml
services:
  king-detective:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 1G
        reservations:
          cpus: '1'
          memory: 512M
```

### 2. 日志轮转

```yaml
services:
  king-detective:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 3. 自动重启

```yaml
services:
  king-detective:
    restart: unless-stopped
```

---

## 🔄 升级流程

### 1. 备份当前版本

```bash
# 备份数据
./scripts/backup.sh

# 记录当前版本
docker images | grep king-detective
```

### 2. 拉取新版本

```bash
git pull
docker-compose build
```

### 3. 平滑升级

```bash
# 停止旧容器
docker-compose down

# 启动新容器
docker-compose up -d

# 验证
docker-compose logs -f
```

### 4. 回滚（如果需要）

```bash
# 回滚代码
git reset --hard <previous-commit>

# 重新构建
docker-compose build
docker-compose up -d
```

---

## 📞 支持

- **GitHub Issues**: https://github.com/tony-wang1990/Wang-Detective/issues
- **Telegram群**: @你的支持群
- **文档**: `docs/FAQ.md`

---

**部署完成！🎉**
