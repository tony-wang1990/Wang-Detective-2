package com.tony.kingdetective.telegram.task;

import com.tony.kingdetective.telegram.storage.SshConnectionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Telegram Bot Scheduled Tasks
 * Clean up expired SSH connections.
 * 
 * @author yohann
 */
@Slf4j
@Component
public class TelegramCleanupTask {
    
    /**
     * Clean up expired SSH connections.
     * Runs every 30 minutes
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void cleanupExpiredData() {
        try {
            // Clean SSH connections
            SshConnectionStorage.getInstance().cleanExpiredConnections();
            log.info("Cleaned up expired SSH connections");
            
        } catch (Exception e) {
            log.error("Failed to clean up expired data", e);
        }
    }
}
