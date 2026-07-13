package com.tony.kingdetective;

import com.tony.kingdetective.bean.params.ops.SshCredentialParams;
import com.tony.kingdetective.bean.params.ops.SshHostSaveParams;
import com.tony.kingdetective.bean.response.ops.SshHostRsp;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.IOciKvService;
import com.tony.kingdetective.service.AdminCredentialService;
import com.tony.kingdetective.service.ops.SshHostService;
import com.tony.kingdetective.service.ops.WebSshSessionRegistry;
import com.tony.kingdetective.telegram.handler.CallbackHandler;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.CustomExpiryGuavaCache;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/king-detective-test-${random.uuid}.db",
        "king-detective.startup.tasks-enabled=false",
        "king-detective.websocket.server-endpoint-exporter-enabled=false",
        "oci-cfg.key-dir-path=target/test-keys"
})
class OciHelperApplicationTests {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private IOciKvService kvService;

    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Resource
    private WebSshSessionRegistry webSshSessionRegistry;

    @Resource
    private SshHostService sshHostService;

    @Resource
    private IAuditLogService auditLogService;

    @Resource
    private AdminCredentialService adminCredentialService;

    @Resource
    private MockMvc mockMvc;

    @Test
    void contextLoadsWithLocalTestDatabase() {
        assertThat(applicationContext).isNotNull();
        assertThat(kvService.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void customCacheExpiresValues() throws InterruptedException {
        customCache.put("cache-test-key", "value", 50);

        assertThat(customCache.get("cache-test-key")).isEqualTo("value");
        Thread.sleep(80);
        assertThat(customCache.get("cache-test-key")).isNull();
    }

    @Test
    void webSshSessionRegistryStoresAndRemovesCredential() {
        SshCredentialParams credential = new SshCredentialParams();
        credential.setHost("127.0.0.1");
        credential.setUsername("opc");

        WebSshSessionRegistry.Entry entry = webSshSessionRegistry.create(credential, 1);

        assertThat(webSshSessionRegistry.getCredential(entry.sessionId())).isSameAs(credential);

        webSshSessionRegistry.remove(entry.sessionId());
        assertThat(webSshSessionRegistry.getCredential(entry.sessionId())).isNull();
    }

    @Test
    void sshHostServiceStoresSecretsEncryptedAndResolvesCredential() {
        SshHostSaveParams params = new SshHostSaveParams();
        params.setName("test-localhost");
        params.setHost("127.0.0.1");
        params.setPort(22);
        params.setUsername("opc");
        params.setPassword("secret-password");
        params.setTags("test");

        SshHostRsp saved = sshHostService.create(params);

        try {
            assertThat(saved.getHasPassword()).isTrue();
            assertThat(saved.getHasPrivateKey()).isFalse();

            SshCredentialParams credential = sshHostService.credentialForHost(saved.getId());
            assertThat(credential.getHost()).isEqualTo("127.0.0.1");
            assertThat(credential.getUsername()).isEqualTo("opc");
            assertThat(credential.getPassword()).isEqualTo("secret-password");
        } finally {
            sshHostService.delete(saved.getId());
        }
    }

    @Test
    void auditLogServiceWritesRecentRecords() {
        auditLogService.logSuccess("test-user", "OPS_TEST_AUDIT", "target-1", "details");

        assertThat(auditLogService.recent(20))
                .anySatisfy(item -> {
                    assertThat(item.getOperation()).isEqualTo("OPS_TEST_AUDIT");
                    assertThat(item.getTarget()).isEqualTo("target-1");
                    assertThat(item.getSuccess()).isTrue();
                });
    }

    @Test
    void unknownApiRouteReturnsJson404InsteadOfSpaIndex() throws Exception {
        String token = adminCredentialService.generateToken(Map.of("account", adminCredentialService.getAccount()));

        mockMvc.perform(get("/api/route-that-does-not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":404")));
    }

    @Test
    void actuatorLivenessRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void changingAdminCredentialInvalidatesExistingTokens() {
        String oldToken = adminCredentialService.generateToken(Map.of("account", "admin"));

        adminCredentialService.updateCredential(
                "admin123456",
                "audit-admin",
                "audit-password-123",
                "audit-password-123"
        );
        try {
            assertThat(adminCredentialService.verifyToken(oldToken)).isFalse();
            assertThat(adminCredentialService.matches("audit-admin", "audit-password-123")).isTrue();
        } finally {
            adminCredentialService.updateCredential(
                    "audit-password-123",
                    "admin",
                    "admin123456",
                    "admin123456"
            );
        }
    }

    @Test
    void telegramCallbackHandlersInitializeWithUniquePatterns() {
        Map<String, CallbackHandler> handlers = applicationContext.getBeansOfType(CallbackHandler.class);

        assertThat(handlers).hasSizeGreaterThan(100);
        HashSet<String> patterns = new HashSet<>();
        handlers.forEach((beanName, handler) -> {
            String pattern = handler.getCallbackPattern();
            assertThat(pattern)
                    .as("callback pattern for %s", beanName)
                    .isNotBlank();
            assertThat(patterns.add(pattern))
                    .as("duplicate callback pattern: %s", pattern)
                    .isTrue();
        });
    }

    @Test
    void clientFeatureGatewayRequiresAdminTokenAndExposesAllHandlers() throws Exception {
        int handlerCount = applicationContext.getBeansOfType(CallbackHandler.class).size();
        String token = adminCredentialService.generateToken(Map.of("account", adminCredentialService.getAccount()));

        mockMvc.perform(get("/api/v1/client-features/menu"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/client-features/menu")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registeredHandlerCount").value(handlerCount))
                .andExpect(jsonPath("$.data.buttons").isArray());
    }

    @Test
    void forwardedClientIpIsTrustedOnlyFromPrivateProxyNetworks() {
        MockHttpServletRequest publicRequest = new MockHttpServletRequest();
        publicRequest.setRemoteAddr("203.0.113.10");
        publicRequest.addHeader("X-Forwarded-For", "198.51.100.20");
        assertThat(CommonUtils.getClientIP(publicRequest)).isEqualTo("203.0.113.10");

        MockHttpServletRequest proxyRequest = new MockHttpServletRequest();
        proxyRequest.setRemoteAddr("172.18.0.2");
        proxyRequest.addHeader("X-Forwarded-For", "198.51.100.20, 172.18.0.2");
        assertThat(CommonUtils.getClientIP(proxyRequest)).isEqualTo("198.51.100.20");
    }
}
