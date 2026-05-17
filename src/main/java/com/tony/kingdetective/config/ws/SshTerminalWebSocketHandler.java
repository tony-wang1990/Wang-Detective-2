package com.tony.kingdetective.config.ws;

import cn.hutool.jwt.JWTUtil;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.tony.kingdetective.bean.params.ops.SshCredentialParams;
import com.tony.kingdetective.service.ops.WebSshService;
import com.tony.kingdetective.service.ops.WebSshSessionRegistry;
import com.tony.kingdetective.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {
    private static final String RESIZE_PREFIX = "__KD_RESIZE__:";
    private final WebSshService webSshService;
    private final WebSshSessionRegistry sessionRegistry;
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${web.password}")
    private String password;

    public SshTerminalWebSocketHandler(WebSshService webSshService, WebSshSessionRegistry sessionRegistry) {
        this.webSshService = webSshService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        String token = getQueryParam(webSocketSession, "token");
        if (!validateToken(token)) {
            webSocketSession.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }

        String sessionId = extractSessionId(webSocketSession);
        SshCredentialParams credential = sessionRegistry.getCredential(sessionId);
        if (credential == null) {
            webSocketSession.close(CloseStatus.POLICY_VIOLATION.withReason("SSH session expired"));
            return;
        }
        sessionRegistry.touchConnected(sessionId);

        Session sshSession = webSshService.openSession(credential);
        ChannelShell shell = (ChannelShell) sshSession.openChannel("shell");
        shell.setPty(true);
        shell.setPtyType("xterm");
        InputStream remoteOutput = shell.getInputStream();
        OutputStream remoteInput = shell.getOutputStream();
        shell.connect();

        TerminalSession terminalSession = new TerminalSession(sshSession, shell, remoteInput, null);
        sessions.put(webSocketSession.getId(), terminalSession);
        safeSend(webSocketSession, "\r\nConnected to " + credential.getUsername() + "@" + credential.getHost() + "\r\n");

        Future<?> pumpTask = executor.submit(() -> pumpOutput(webSocketSession, remoteOutput));
        terminalSession.setPumpTask(pumpTask);
    }

    @Override
    protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) throws Exception {
        TerminalSession terminalSession = sessions.get(webSocketSession.getId());
        if (terminalSession == null || terminalSession.shell() == null || terminalSession.shell().isClosed()) {
            webSocketSession.close(CloseStatus.SERVER_ERROR.withReason("SSH shell is closed"));
            return;
        }
        String payload = message.getPayload();
        if (payload != null && payload.startsWith(RESIZE_PREFIX)) {
            resizeTerminal(terminalSession.shell(), payload);
            return;
        }
        terminalSession.remoteInput().write(payload.getBytes(StandardCharsets.UTF_8));
        terminalSession.remoteInput().flush();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
        closeTerminal(webSocketSession.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable exception) {
        log.warn("SSH terminal WebSocket transport error: {}", exception.getMessage());
        closeTerminal(webSocketSession.getId());
    }

    private void pumpOutput(WebSocketSession webSocketSession, InputStream remoteOutput) {
        byte[] buffer = new byte[4096];
        try {
            int read;
            while (webSocketSession.isOpen() && (read = remoteOutput.read(buffer)) >= 0) {
                if (read > 0) {
                    safeSend(webSocketSession, new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            if (webSocketSession.isOpen()) {
                safeSend(webSocketSession, "\r\nSSH terminal disconnected: " + e.getMessage() + "\r\n");
            }
        } finally {
            closeTerminal(webSocketSession.getId());
        }
    }

    private void closeTerminal(String webSocketSessionId) {
        TerminalSession terminalSession = sessions.remove(webSocketSessionId);
        if (terminalSession == null) {
            return;
        }
        if (terminalSession.pumpTask() != null) {
            terminalSession.pumpTask().cancel(true);
        }
        try {
            terminalSession.remoteInput().close();
        } catch (Exception ignored) {
        }
        if (terminalSession.shell() != null) {
            terminalSession.shell().disconnect();
        }
        if (terminalSession.sshSession() != null) {
            terminalSession.sshSession().disconnect();
        }
    }

    private void safeSend(WebSocketSession webSocketSession, String payload) {
        synchronized (webSocketSession) {
            try {
                if (webSocketSession.isOpen()) {
                    webSocketSession.sendMessage(new TextMessage(payload));
                }
            } catch (Exception e) {
                log.warn("Failed to send SSH terminal output: {}", e.getMessage());
            }
        }
    }

    private void resizeTerminal(ChannelShell shell, String payload) {
        String[] parts = payload.substring(RESIZE_PREFIX.length()).split(":");
        if (parts.length < 2) {
            return;
        }
        try {
            int cols = Math.max(40, Math.min(Integer.parseInt(parts[0]), 240));
            int rows = Math.max(10, Math.min(Integer.parseInt(parts[1]), 80));
            shell.setPtySize(cols, rows, cols * 8, rows * 16);
        } catch (Exception e) {
            log.debug("Ignore invalid SSH terminal resize payload: {}", payload);
        }
    }

    private boolean validateToken(String token) {
        return token != null
                && !CommonUtils.isTokenExpired(token)
                && JWTUtil.verify(token, password.getBytes(StandardCharsets.UTF_8));
    }

    private String extractSessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            return "";
        }
        String path = session.getUri().getPath();
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private String getQueryParam(WebSocketSession session, String name) {
        if (session.getUri() == null || session.getUri().getRawQuery() == null) {
            return null;
        }
        for (String pair : session.getUri().getRawQuery().split("&")) {
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

    private static class TerminalSession {
        private final Session sshSession;
        private final ChannelShell shell;
        private final OutputStream remoteInput;
        private Future<?> pumpTask;

        private TerminalSession(Session sshSession, ChannelShell shell, OutputStream remoteInput, Future<?> pumpTask) {
            this.sshSession = sshSession;
            this.shell = shell;
            this.remoteInput = remoteInput;
            this.pumpTask = pumpTask;
        }

        public Session sshSession() {
            return sshSession;
        }

        public ChannelShell shell() {
            return shell;
        }

        public OutputStream remoteInput() {
            return remoteInput;
        }

        public Future<?> pumpTask() {
            return pumpTask;
        }

        public void setPumpTask(Future<?> pumpTask) {
            this.pumpTask = pumpTask;
        }
    }
}
