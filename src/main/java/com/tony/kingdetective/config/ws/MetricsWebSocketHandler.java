package com.tony.kingdetective.config.ws;

import cn.hutool.core.map.MapUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.service.AdminCredentialService;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
@ServerEndpoint("/metrics")
public class MetricsWebSocketHandler {

    private static final ConcurrentHashMap<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Future<?>> FUTURE_MAP = new ConcurrentHashMap<>();
    private static final ExecutorService METRICS_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final int INTERVAL_SECONDS = 5;
    private static final int HISTORY_SIZE = 15;

    private boolean validateToken(String token) {
        return SpringUtil.getBean(AdminCredentialService.class).verifyToken(token);
    }

    @OnOpen
    public void onOpen(Session session) {
        String token = getQueryParam(session, "token");
        if (token == null || !validateToken(token)) {
            throw new OciException(-1, "无效的 token");
        }
        SESSION_MAP.put(session.getId(), session);
        startMetricsTask(session);
    }

    @OnClose
    public void onClose(Session session) {
        SESSION_MAP.remove(session.getId());
        Future<?> future = FUTURE_MAP.remove(session.getId());
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    @OnMessage
    public void onMessage(String message) {
        log.debug("Metrics WebSocket client message: {}", message);
    }

    private void startMetricsTask(Session session) {
        Future<?> future = METRICS_EXECUTOR.submit(() -> {
            SystemInfo systemInfo = new SystemInfo();
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            NetworkIF networkIF = primaryNetworkInterface(systemInfo);
            long[] previousCpuTicks = processor.getSystemCpuLoadTicks();
            long previousRxBytes = 0L;
            long previousTxBytes = 0L;
            List<String> timestamps = new LinkedList<>();
            List<Double> inRates = new LinkedList<>();
            List<Double> outRates = new LinkedList<>();

            if (networkIF != null) {
                networkIF.updateAttributes();
                previousRxBytes = networkIF.getBytesRecv();
                previousTxBytes = networkIF.getBytesSent();
            }

            while (session.isOpen() && SESSION_MAP.containsKey(session.getId())) {
                try {
                    Thread.sleep(INTERVAL_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                Map<String, Object> metrics = new HashMap<>();
                double cpuUsed = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100;
                previousCpuTicks = processor.getSystemCpuLoadTicks();
                double memoryUsed = memory.getTotal() == 0 ? 0 : ((double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal()) * 100;
                metrics.put("cpuUsage", MapUtil.builder()
                        .put("used", percent(cpuUsed))
                        .put("free", percent(100 - cpuUsed))
                        .build());
                metrics.put("memoryUsage", MapUtil.builder()
                        .put("used", percent(memoryUsed))
                        .put("free", percent(100 - memoryUsed))
                        .build());

                if (networkIF != null) {
                    networkIF.updateAttributes();
                    long rxBytes = networkIF.getBytesRecv();
                    long txBytes = networkIF.getBytesSent();
                    addLimited(inRates, roundKbPerSecond(rxBytes - previousRxBytes));
                    addLimited(outRates, roundKbPerSecond(txBytes - previousTxBytes));
                    previousRxBytes = rxBytes;
                    previousTxBytes = txBytes;
                    Calendar calendar = Calendar.getInstance();
                    addLimited(timestamps, String.format("%02d:%02d:%02d",
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            calendar.get(Calendar.SECOND)));
                }

                timestamps.sort((t1, t2) -> LocalTime.parse(t1).compareTo(LocalTime.parse(t2)));
                metrics.put("trafficData", MapUtil.builder()
                        .put("timestamps", new ArrayList<>(timestamps))
                        .put("inbound", new ArrayList<>(inRates))
                        .put("outbound", new ArrayList<>(outRates))
                        .build());
                sendOneMessage(session, JSONUtil.toJsonStr(metrics));
            }
        });
        FUTURE_MAP.put(session.getId(), future);
    }

    private NetworkIF primaryNetworkInterface(SystemInfo systemInfo) {
        return systemInfo.getHardware().getNetworkIFs().stream()
                .filter(NetworkIF::isConnectorPresent)
                .filter(iface -> !Arrays.asList(iface.getIPv4addr()).isEmpty() || !Arrays.asList(iface.getIPv6addr()).isEmpty())
                .filter(iface -> iface.getName().startsWith("e"))
                .min((a, b) -> Long.compare(b.getSpeed(), a.getSpeed()))
                .orElse(null);
    }

    private String percent(double value) {
        double safe = Math.max(0, Math.min(100, value));
        return String.format(Locale.ROOT, "%.2f", safe);
    }

    private double roundKbPerSecond(long bytesDelta) {
        double kbPerSecond = Math.max(0, bytesDelta) / 1024.0 / INTERVAL_SECONDS;
        return Double.parseDouble(String.format(Locale.ROOT, "%.2f", kbPerSecond));
    }

    private <T> void addLimited(List<T> values, T value) {
        if (values.size() == HISTORY_SIZE) {
            values.remove(0);
        }
        values.add(value);
    }

    private void sendOneMessage(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.getAsyncRemote().sendText(message);
            } catch (Exception e) {
                log.warn("Failed to push metrics data: {}", e.getMessage());
            }
        }
    }

    private String getQueryParam(Session session, String name) {
        if (session == null || session.getQueryString() == null) {
            return null;
        }
        for (String pair : session.getQueryString().split("&")) {
            int splitIndex = pair.indexOf('=');
            if (splitIndex <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, splitIndex), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return URLDecoder.decode(pair.substring(splitIndex + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
