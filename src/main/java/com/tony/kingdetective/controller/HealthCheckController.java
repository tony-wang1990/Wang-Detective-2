package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.vo.HealthStatus;
import com.tony.kingdetective.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;

/**
 * 健康检查控制器
 * 提供应用健康状态查询接口
 * 
 * @author Tony Wang
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
public class HealthCheckController {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public HealthStatus health() {
        log.debug("执行健康检查");
        
        boolean databaseOk = checkDatabase();
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        boolean memoryOk = checkMemory(usedMemory, maxMemory);
        
        // Container liveness must describe whether the application can serve requests.
        // High JVM usage is still exposed as memoryStatus=false, but it must not make an
        // otherwise available service permanently unhealthy on a 1 GB VPS.
        String status = databaseOk ? "UP" : "DOWN";
        
        return HealthStatus.builder()
                .status(status)
                .databaseConnectivity(databaseOk)
                .memoryStatus(memoryOk)
                .usedMemoryBytes(usedMemory)
                .maxMemoryBytes(maxMemory)
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .version(getVersion())
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 检查数据库连接
     */
    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5秒超时
        } catch (Exception e) {
            log.error("数据库连接检查失败", e);
            return false;
        }
    }
    
    /**
     * 检查内存状态
     */
    private boolean checkMemory(long usedMemory, long maxMemory) {
        try {
            double usagePercent = (double) usedMemory / maxMemory * 100;
            
            log.debug("内存使用率: {}%", String.format("%.2f", usagePercent));
            
            // 如果内存使用率超过90%则认为不健康
            return usagePercent < 90;
        } catch (Exception e) {
            log.error("内存检查失败", e);
            return false;
        }
    }

    private String getVersion() {
        return CommonUtils.getCurrentVersion();
    }
}
