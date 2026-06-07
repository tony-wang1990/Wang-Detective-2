package com.tony.kingdetective.config.auth;

import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.service.AdminCredentialService;
import com.tony.kingdetective.service.IIpBlacklistService;
import com.tony.kingdetective.service.IOciKvService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String DEFENSE_MODE_KEY = "defense_mode_enabled";
    private static final long DEFENSE_MODE_CACHE_MILLIS = 10_000L;

    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/sys/login",
            "/api/sys/getEnableMfa",
            "/api/sys/googleLogin",
            "/api/sys/getGoogleClientId"
    );

    private final AdminCredentialService adminCredentialService;
    private volatile boolean defenseModeEnabledCache;
    private volatile long defenseModeCacheExpiresAt;

    public AuthInterceptor(AdminCredentialService adminCredentialService) {
        this.adminCredentialService = adminCredentialService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("GET".equalsIgnoreCase(request.getMethod()) && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        String clientIp = getClientIp(request);
        if (isDefenseModeEnabled()) {
            log.warn("Defense mode is enabled, blocking request from IP: {}", clientIp);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "{\"code\":403,\"msg\":\"防御模式已开启，访问被拒绝\"}");
            return false;
        }

        IIpBlacklistService blacklistService = SpringUtil.getBean(IIpBlacklistService.class);
        if (blacklistService.isBlacklisted(clientIp)) {
            log.warn("IP {} is blacklisted", clientIp);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "{\"code\":403,\"msg\":\"当前 IP 已被拦截\"}");
            return false;
        }

        if (requiresToken(request.getRequestURI())) {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);
                if (adminCredentialService.verifyToken(token)) {
                    return true;
                }
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            throw new OciException(401, "未授权，请重新登录");
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private boolean requiresToken(String uri) {
        if (uri == null) {
            return false;
        }
        if (PUBLIC_ENDPOINTS.contains(uri)) {
            return false;
        }
        return uri.startsWith("/api/") || uri.startsWith("/chat/");
    }

    private boolean isDefenseModeEnabled() {
        long now = System.currentTimeMillis();
        if (now < defenseModeCacheExpiresAt) {
            return defenseModeEnabledCache;
        }
        try {
            IOciKvService kvService = SpringUtil.getBean(IOciKvService.class);
            OciKv defenseModeKv = kvService.getByKey(DEFENSE_MODE_KEY);
            defenseModeEnabledCache = defenseModeKv != null && "true".equals(defenseModeKv.getValue());
            defenseModeCacheExpiresAt = now + DEFENSE_MODE_CACHE_MILLIS;
            return defenseModeEnabledCache;
        } catch (Exception e) {
            log.error("Failed to check defense mode", e);
            return false;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
