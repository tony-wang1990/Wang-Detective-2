package com.tony.kingdetective.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.AuditLog;
import com.tony.kingdetective.bean.entity.LoginAttempt;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.ILoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据维护定时任务
 * 定期清理过期日志并压缩数据库空间
 *
 * @author Tony Wang
 */
@Slf4j
@Component
public class DataMaintenanceTask {

    @Autowired
    private IAuditLogService auditLogService;

    @Autowired
    private ILoginAttemptService loginAttemptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨 3:00 执行数据清理任务
     * 保留最近 30 天的日志数据
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldLogs() {
        log.info("Starting scheduled data maintenance task...");
        
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            // 清理审计日志
            long auditLogRemoved = auditLogService.count(
                    new LambdaQueryWrapper<AuditLog>().lt(AuditLog::getCreateTime, thirtyDaysAgo));
            if (auditLogRemoved > 0) {
                auditLogService.remove(
                        new LambdaQueryWrapper<AuditLog>().lt(AuditLog::getCreateTime, thirtyDaysAgo));
                log.info("Removed {} old audit log records.", auditLogRemoved);
            }

            // 清理登录限制日志
            long loginAttemptRemoved = loginAttemptService.count(
                    new LambdaQueryWrapper<LoginAttempt>().lt(LoginAttempt::getAttemptTime, thirtyDaysAgo));
            if (loginAttemptRemoved > 0) {
                loginAttemptService.remove(
                        new LambdaQueryWrapper<LoginAttempt>().lt(LoginAttempt::getAttemptTime, thirtyDaysAgo));
                log.info("Removed {} old login attempt records.", loginAttemptRemoved);
            }

            // 执行 SQLite VACUUM 释放物理空间
            if (auditLogRemoved > 0 || loginAttemptRemoved > 0) {
                log.info("Executing VACUUM to reclaim disk space...");
                jdbcTemplate.execute("VACUUM");
                log.info("Database vacuum completed successfully.");
            }
            
            log.info("Data maintenance task completed successfully.");
        } catch (Exception e) {
            log.error("Failed to execute data maintenance task", e);
        }
    }
}
