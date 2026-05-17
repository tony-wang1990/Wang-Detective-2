package com.tony.kingdetective.service.ops;

import cn.hutool.core.util.IdUtil;
import com.tony.kingdetective.bean.params.ops.SshCredentialParams;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WebSshSessionRegistry {
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public Entry create(SshCredentialParams credential, int ttlMinutes) {
        cleanExpired();
        long ttlMillis = TimeUnit.MINUTES.toMillis(Math.max(1, Math.min(ttlMinutes, 120)));
        long now = System.currentTimeMillis();
        Entry entry = new Entry(IdUtil.fastSimpleUUID(), credential, now, now + ttlMillis);
        sessions.put(entry.sessionId(), entry);
        return entry;
    }

    public SshCredentialParams getCredential(String sessionId) {
        Entry entry = sessions.get(sessionId);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return null;
        }
        return entry.credential();
    }

    public Entry get(String sessionId) {
        Entry entry = sessions.get(sessionId);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return null;
        }
        return entry;
    }

    public List<Entry> list() {
        cleanExpired();
        return sessions.values().stream()
                .sorted(Comparator.comparingLong(Entry::createdAt).reversed())
                .toList();
    }

    public void touchConnected(String sessionId) {
        Entry entry = get(sessionId);
        if (entry != null) {
            entry.touchConnected();
        }
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public static class Entry {
        private final String sessionId;
        private final SshCredentialParams credential;
        private final long createdAt;
        private final long expiresAt;
        private final AtomicLong lastConnectedAt = new AtomicLong(0);
        private final AtomicInteger connectCount = new AtomicInteger(0);

        public Entry(String sessionId, SshCredentialParams credential, long createdAt, long expiresAt) {
            this.sessionId = sessionId;
            this.credential = credential;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public String sessionId() {
            return sessionId;
        }

        public SshCredentialParams credential() {
            return credential;
        }

        public long createdAt() {
            return createdAt;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public long lastConnectedAt() {
            return lastConnectedAt.get();
        }

        public int connectCount() {
            return connectCount.get();
        }

        public void touchConnected() {
            lastConnectedAt.set(System.currentTimeMillis());
            connectCount.incrementAndGet();
        }
    }
}
