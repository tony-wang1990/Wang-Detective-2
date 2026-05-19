package com.tony.kingdetective.task;

import com.tony.kingdetective.bean.params.sys.BackupParams;
import com.tony.kingdetective.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * feat #17: 定时自动备份任务
 *
 * 通过环境变量控制：
 *   AUTO_BACKUP_ENABLED=true     开启定时备份
 *   AUTO_BACKUP_CRON=0 0 3 * * ? 自定义备份时间（默认每天凌晨3点）
 *   AUTO_BACKUP_PASSWORD=xxx     备份加密密码（可选）
 *
 * @author Tony Wang
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "king-detective.backup",
        name = "auto-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AutoBackupTask {

    @Resource
    private ISysService sysService;

    @Value("${king-detective.backup.password:}")
    private String backupPassword;

    /**
     * 定时执行备份，cron 从配置读取，默认凌晨3点
     *
     * 注意：@Scheduled cron 不支持动态表达式，这里用固定属性占位符
     * 如需动态 cron，需改用 TaskScheduler（见 OciTask 的 dailyBroadcastTask 实现）
     */
    @Scheduled(cron = "${king-detective.backup.cron:0 0 3 * * ?}")
    public void autoBackup() {
        log.info("【定时自动备份】开始执行定时备份任务...");
        try {
            BackupParams params = new BackupParams();
            boolean hasPassword = backupPassword != null && !backupPassword.isBlank();
            params.setEnableEnc(hasPassword);
            params.setPassword(hasPassword ? backupPassword : "");

            sysService.backup(params);

            String notifyMsg = String.format(
                    "【定时自动备份】备份完成\n\n" +
                    "时间：%s\n" +
                    "加密：%s\n" +
                    "状态：成功",
                    java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    hasPassword ? "已启用" : "未启用"
            );
            sysService.sendMessage(notifyMsg);
            log.info("【定时自动备份】备份成功，已推送 TG 通知");

        } catch (Exception e) {
            log.error("【定时自动备份】备份失败：{}", e.getMessage(), e);
            try {
                sysService.sendMessage(
                        "【定时自动备份】备份失败\n\n" +
                        "时间：" + java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                        "错误：" + e.getMessage() + "\n" +
                        "请检查日志或手动触发备份。"
                );
            } catch (Exception notifyEx) {
                log.warn("【定时自动备份】推送失败通知异常：{}", notifyEx.getMessage());
            }
        }
    }
}
