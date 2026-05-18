ALTER TABLE ops_ssh_host ADD COLUMN host_group TEXT DEFAULT '默认分组';

CREATE TABLE IF NOT EXISTS ops_command_template (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    command TEXT NOT NULL,
    category TEXT DEFAULT '常用',
    description TEXT,
    risk_level TEXT DEFAULT 'LOW',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_command_template_name ON ops_command_template(name);
CREATE INDEX IF NOT EXISTS idx_ops_command_template_category ON ops_command_template(category);
CREATE INDEX IF NOT EXISTS idx_ops_command_template_update_time ON ops_command_template(update_time);

INSERT INTO ops_command_template (id, name, command, category, description, risk_level)
SELECT 'tpl-system-overview', '系统概览', 'uname -a && uptime && free -h && df -h', '系统', '查看系统、负载、内存和磁盘概况', 'LOW'
WHERE NOT EXISTS (SELECT 1 FROM ops_command_template WHERE id = 'tpl-system-overview');

INSERT INTO ops_command_template (id, name, command, category, description, risk_level)
SELECT 'tpl-docker-status', 'Docker 状态', 'docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" && docker stats --no-stream', 'Docker', '查看容器列表和瞬时资源使用', 'LOW'
WHERE NOT EXISTS (SELECT 1 FROM ops_command_template WHERE id = 'tpl-docker-status');

INSERT INTO ops_command_template (id, name, command, category, description, risk_level)
SELECT 'tpl-app-log', 'W 探长日志', 'tail -n 160 /var/log/king-detective.log', '应用', '查看应用最近日志', 'LOW'
WHERE NOT EXISTS (SELECT 1 FROM ops_command_template WHERE id = 'tpl-app-log');

INSERT INTO ops_command_template (id, name, command, category, description, risk_level)
SELECT 'tpl-port-listen', '端口监听', 'ss -tulpn | grep -E ":80|:443|:9527" || true', '网络', '查看常用端口监听状态', 'LOW'
WHERE NOT EXISTS (SELECT 1 FROM ops_command_template WHERE id = 'tpl-port-listen');
