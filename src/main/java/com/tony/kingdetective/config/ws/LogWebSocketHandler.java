package com.tony.kingdetective.config.ws;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import com.tony.kingdetective.service.AdminCredentialService;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.TextEncodingUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

/**
 * @projectName: king-detective
 * @package: com.tony.kingdetective.utils
 * @className: LogWebSocketHandler
 * @author: Tony Wang
 * @date: 2024/11/17 18:21
 */
@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {
    private static WebSocketSession currentSession;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService pushThreadExecutor = Executors.newSingleThreadExecutor();
    private final Deque<String> recentLogs = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT_LOGS = 200;
    Tailer tailer;
    Future<?> logPushTask;
    private volatile boolean close = false;
    private volatile boolean isSenderRunning = false;

    private final AdminCredentialService adminCredentialService;

    public LogWebSocketHandler(AdminCredentialService adminCredentialService) {
        this.adminCredentialService = adminCredentialService;
    }

    private String getTokenFromSession(WebSocketSession session) {
        // 解析 URI 中的 token 参数
        if (session.getUri() == null || session.getUri().getRawQuery() == null) {
            return null;
        }
        String query = session.getUri().getRawQuery();
        for (String pair : query.split("&")) {
            int splitIndex = pair.indexOf('=');
            if (splitIndex <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, splitIndex), StandardCharsets.UTF_8);
            if ("token".equals(key)) {
                return URLDecoder.decode(pair.substring(splitIndex + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        return adminCredentialService.verifyToken(token);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = getTokenFromSession(session);
        if (token == null || !validateToken(token)) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            } catch (IOException e) {
                log.warn("Failed to close unauthorized log WebSocket session", e);
            }
            return;
        }

        close = false;
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
            } catch (IOException e) {
                log.error("Error while closing old WebSocket session: {}", e.getLocalizedMessage());
            }
        }
        currentSession = session;

        try {
            startLogTailer(CommonUtils.LOG_FILE_PATH);
        } catch (Exception e) {
            if (e.getLocalizedMessage().contains("Negative seek offset")) {
                List<String> readUtf8Lines = FileUtil.readUtf8Lines(CommonUtils.LOG_FILE_PATH);
                readUtf8Lines.add("\n");
                FileUtil.writeUtf8Lines(new ArrayList<>(MAX_RECENT_LOGS), CommonUtils.LOG_FILE_PATH);
                FileUtil.writeUtf8Lines(readUtf8Lines, CommonUtils.LOG_FILE_PATH);
                tailer.stop();
                startLogTailer(CommonUtils.LOG_FILE_PATH);
            } else {
                log.error("启动日志监听服务失败：{}", e.getLocalizedMessage(), e);
            }
        }

        sendRecentLogs();
        startMessageSender();
    }

    private void sendRecentLogs() {
        if (currentSession == null || !currentSession.isOpen() || close) {
            return;
        }

        synchronized (recentLogs) {
            recentLogs.forEach(recentLog -> {
                try {
                    currentSession.sendMessage(new TextMessage(TextEncodingUtils.repairMojibake(recentLog)));
                } catch (IOException e) {
                    log.error("Error while sending recent log: {}", e.getLocalizedMessage());
                }
            });
        }
    }

    private void startLogTailer(String filePath) {
        File logFile = new File(filePath);
        if (!logFile.exists()) {
            FileUtil.touch(logFile);
        }
        if (!logFile.isFile()) {
            throw new IllegalStateException("Invalid log file path: " + filePath);
        }

        tailer = new Tailer(logFile, StandardCharsets.UTF_8, line -> {
            try {
                if (!close) {
                    String displayLine = TextEncodingUtils.repairMojibake(line);
                    messageQueue.put(displayLine);

                    synchronized (recentLogs) {
                        if (recentLogs.size() >= MAX_RECENT_LOGS) {
                            recentLogs.pollFirst();
                        }
                        recentLogs.addLast(displayLine);
                    }
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("Failed to enqueue log line: {}", e.getLocalizedMessage());
            }
        }, MAX_RECENT_LOGS, 1000);
        tailer.start(true);
    }

    private void startMessageSender() {
        if (isSenderRunning) {
            return;
        }
        isSenderRunning = true;

        logPushTask = pushThreadExecutor.submit(() -> {
            try {
                while (!close) {
                    String message = messageQueue.take();
                    synchronized (LogWebSocketHandler.class) {
                        if (currentSession != null && currentSession.isOpen()) {
                            currentSession.sendMessage(new TextMessage(message));
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error while sending WebSocket message: {}", e.getLocalizedMessage());
            } finally {
                isSenderRunning = false;
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            close = true;
            if (tailer != null) {
                tailer.stop();
            }
            if (logPushTask != null) {
                logPushTask.cancel(true);
            }
            if (session == currentSession) {
                if (currentSession.isOpen()) {
                    currentSession.close();
                }
                currentSession = null;
            } else if (session.isOpen()) {
                session.close();
            }
            messageQueue.clear();
        } catch (Exception e) {
            log.error("WebSocket session closed: {}", session.getId());
        }
    }
}
