package com.tony.kingdetective.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;

@Slf4j
@Component
public class StartupPerformanceReporter {

    @EventListener
    public void report(ApplicationReadyEvent event) {
        Duration timeTaken = event.getTimeTaken();
        Runtime runtime = Runtime.getRuntime();
        double systemLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        log.info(
                "Startup performance: readyIn={}s, processors={}, systemLoad={}, heapUsed={}MB, heapMax={}MB",
                timeTaken == null ? "unknown" : String.format("%.2f", timeTaken.toMillis() / 1000.0),
                runtime.availableProcessors(),
                systemLoad < 0 ? "unknown" : String.format("%.2f", systemLoad),
                usedMemory / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024
        );

        if (systemLoad >= runtime.availableProcessors() * 2.0) {
            log.warn(
                    "Host load is much higher than the CPU available to the JVM; startup and OCI requests may be delayed"
            );
        }
    }
}
