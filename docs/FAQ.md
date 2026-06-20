# ❓ King-Detective FAQ

## 常见问题

### 🤖 Telegram Bot

#### Q: Bot不响应怎么办？
**A**: 
1. 检查Token是否正确设置
```bash
docker logs king-detective | grep "TELEGRAM_BOT_TOKEN"
```
2. 验证Bot是否启动成功
```bash
docker logs king-detective | grep "Bot started"
```
3. 检查网络连接
```bash
docker exec king-detective ping api.telegram.org
```

#### Q: 如何更换Bot Token？
**A**:
1. 在BotFather撤销旧Token
2. 获取新Token
3. 更新`.env`文件
4. 重启服务：`docker-compose restart`

#### Q: Bot菜单按钮无反应？
**A**:
- 检查Handler是否注册
- 查看日志中的错误信息
- 确认回调数据匹配

---

### 🔐 安全功能

#### Q: 防御模式开启后如何关闭？
**A**: 
只能通过Telegram Bot关闭：
1. 发送 `/start` 或 `/menu`
2. 点击"安全管理"
3. 点击"关闭防御模式"

**注意**: Web端无法操作（这是设计目的）

#### Q: IP被误拉黑怎么办？
**A**:
1. 通过Telegram Bot进入"安全管理"
2. 点击"IP黑名单管理"
3. 点击"清空黑名单"
4. 或等待管理员手动移除

#### Q: 登录失败5次后多久解除？
**A**:
- 登录失败记录30分钟后自动过期
- 但IP黑名单**不会自动解除**
- 需要手动清空黑名单

#### Q: 如何防止自己的IP被拉黑？
**A**:
- 正确输入密码
- 登录成功后失败计数会清零
- 建议使用密码管理器

---

### 💾 数据库

#### Q: 如何备份数据库？
**A**:
```bash
# 手动备份
cp data/king-detective.db backups/backup-$(date +%Y%m%d).db

# 定时备份（crontab）
0 2 * * * cp /opt/king-detective/data/king-detective.db /backups/db-$(date +\%Y\%m\%d).db
```

#### Q: 数据库损坏如何恢复？
**A**:
```bash
# 1. 停止服务
docker-compose down

# 2. 恢复备份
cp backups/latest.db data/king-detective.db

# 3. 重启服务
docker-compose up -d
```

#### Q: 如何清空所有数据？
**A**:
```bash
# 警告：不可恢复！
docker-compose down
rm data/king-detective.db
docker-compose up -d
```

---

### 🌐 OCI集成

#### Q: 如何添加OCI配置？
**A**:
1. 准备私钥文件（.pem）和配置文件
2. 通过Web端或TG Bot上传
3. 验证配置是否正确

#### Q: 为什么有些区域无法创建实例？
**A**:
- 检查区域配额
- 通过"配额查询"功能查看
- 某些区域可能没有免费资源

#### Q: 自动开机不工作？
**A**:
1. 检查任务是否启用
2. 查看任务日志
3. 验证OCI凭据是否过期

---

### 📊 性能

#### Q: Web端响应慢怎么办？
**A**:
1. 启用缓存（CacheConfig已配置）
2. 增加服务器资源
3. 使用Nginx反向代理

#### Q: 内存占用过高？
**A**:
```bash
# 查看内存使用
docker stats king-detective

# 限制内存
# 在docker-compose.yml中：
deploy:
  resources:
    limits:
      memory: 1G
```

#### Q: 如何优化数据库查询？
**A**:
- 已添加索引（ip_address, ip_blacklist等）
- 启用缓存（defense_mode, oci_kv）
- 定期清理过期数据

---

### 🔧 部署

#### Q: Docker镜像构建失败？
**A**:
```bash
# 清理缓存
docker system prune -a

# 重新构建
docker-compose build --no-cache
```

#### Q: 端口9527被占用？
**A**:
```bash
# 查看占用进程
netstat -tunlp | grep 9527

# 修改端口（docker-compose.yml）
ports:
  - "8080:9527"
```

#### Q: 如何使用HTTPS？
**A**:
使用Nginx + Let's Encrypt：
```bash
sudo certbot --nginx -d your-domain.com
```

---

### 🐛 错误排查

#### Q: "Database locked" 错误？
**A**:
```bash
# 检查是否有多个实例
docker ps | grep king-detective

# 重启服务
docker-compose restart
```

#### Q: "Token过期"错误？
**A**:
- 重新登录获取新Token
- 检查系统时间是否正确

#### Q: API返回403 Forbidden？
**A**:
1. 检查是否在IP黑名单中
2. 检查防御模式是否开启
3. 验证Token是否有效

---

### 📝 配置

#### Q: 如何修改Web端口？
**A**:
```yaml
# docker-compose.yml
ports:
  - "8080:9527"  # 外部:内部
```

#### Q: 如何修改管理员密码？
**A**:
```bash
# 更新.env文件
WEB_PASSWORD=新密码

# 重启服务
docker-compose restart
```

### 🔄 更新

#### Q: 如何更新到最新版本？
**A**:
```bash
# 1. 备份
./scripts/backup.sh

# 2. 拉取更新
git pull

# 3. 重新构建
docker-compose build

# 4. 重启
docker-compose down && docker-compose up -d
```

#### Q: 更新后配置丢失？
**A**:
- 数据在`data/`目录，不会丢失
- 检查`.env`文件是否保留
- 恢复备份

---

### 💡 最佳实践

#### Q: 生产环境建议配置？
**A**:
- ✅ 使用HTTPS
- ✅ 启用防火墙
- ✅ 定期备份数据库
- ✅ 监控日志
- ✅ 使用强密码
- ✅ 启用MFA
- ✅ 限制登录IP（防火墙）

#### Q: 如何监控服务状态？
**A**:
```bash
# 健康检查
curl http://localhost:9527/actuator/health

# 查看日志
docker logs -f king-detective

# 资源使用
docker stats king-detective
```

---

### 🆘 紧急情况

#### Q: 服务崩溃无法启动？
**A**:
```bash
# 1. 查看日志
docker logs king-detective

# 2. 检查数据库
sqlite3 data/king-detective.db "PRAGMA integrity_check;"

# 3. 恢复备份
cp backups/latest.db data/king-detective.db
docker-compose restart
```

#### Q: 被攻击怎么办？
**A**:
1. **立即开启防御模式**（通过TG Bot）
2. 查看IP黑名单
3. 检查日志寻找攻击源
4. 必要时重启服务器

---

## 📞 获取帮助

### 在线资源
- **文档**: `docs/` 目录
- **API文档**: `docs/API.md`
- **部署指南**: `docs/DEPLOYMENT.md`

### 社区支持
- **GitHub Issues**: 报告Bug和功能请求
- **Telegram群**: 技术讨论
- **Email**: support@example.com

### 日志分析
```bash
# 查看错误
docker logs king-detective 2>&1 | grep ERROR

# 查看警告
docker logs king-detective 2>&1 | grep WARN

# 导出日志
docker logs king-detective > logs/debug-$(date +%Y%m%d).log
```

---

**还有问题？提交Issue或加入Telegram群！** 💬
