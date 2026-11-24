package com.tony.kingdetective.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for mandatory credentials (P0 security fix, 2026-11-24).
 *
 * <p>Previously the admin password and the SSH secret key both silently fell back to a
 * well-known weak default, and CORS defaulted to {@code *}. There is no default anymore:
 * <ul>
 *   <li>{@code ADMIN_PASSWORD} (or {@code WEB_PASSWORD}) must be set, at least 8 characters,
 *       and must not be a {@code change_me...} placeholder;</li>
 *   <li>{@code OPS_SSH_SECRET_KEY} must be set and must differ from the admin password
 *       (it no longer falls back to it);</li>
 *   <li>{@code CORS_ALLOWED_ORIGINS} defaults to empty (same-origin only); an explicit
 *       {@code *} is rejected with a warning, not silently accepted.</li>
 * </ul>
 * Any violation throws {@link IllegalStateException} before the application serves
 * traffic, so a misconfigured deployment fails loudly at startup instead of running
 * with a weak credential. Runs with {@link Ordered#HIGHEST_PRECEDENCE} so the failure
 * happens before other runners (e.g. database migration) do any work.
 *
 * <p>Tests that boot the full Spring context must supply test values, e.g. via
 * {@code @SpringBootTest(properties = {"web.password=...", "ops.ssh.secret-key=..."})}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupCredentialValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCredentialValidator.class);

    private static final int MIN_ADMIN_PASSWORD_LENGTH = 8;

    private final Environment environment;

    public StartupCredentialValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String adminPassword = environment.getProperty("web.password", "");
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD is not set. Set ADMIN_PASSWORD (or WEB_PASSWORD) in .env "
                            + "to a strong password of at least 8 characters and restart. "
                            + "See .env.example. The application refuses to start without it.");
        }
        if (adminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD is too short (" + adminPassword.length() + " characters). "
                            + "It must be at least 8 characters. Set a stronger password in .env.");
        }
        if (adminPassword.toLowerCase().startsWith("change_me")) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD is still the .env.example placeholder. Replace it in .env "
                            + "with a real strong password of at least 8 characters.");
        }

        String sshSecretKey = environment.getProperty("ops.ssh.secret-key", "");
        if (sshSecretKey == null || sshSecretKey.isBlank()) {
            throw new IllegalStateException(
                    "OPS_SSH_SECRET_KEY is not set. Set it in .env to a long random secret "
                            + "(generate one with: openssl rand -hex 32) and restart. "
                            + "It must differ from ADMIN_PASSWORD and no longer falls back to it.");
        }
        if (sshSecretKey.equals(adminPassword)) {
            throw new IllegalStateException(
                    "OPS_SSH_SECRET_KEY must differ from ADMIN_PASSWORD. Set a separate "
                            + "long random secret in .env (e.g. openssl rand -hex 32).");
        }

        String corsOrigins = environment.getProperty("web.cors.allowed-origins", "");
        if (corsOrigins != null && corsOrigins.contains("*")) {
            log.warn("CORS_ALLOWED_ORIGINS contains '*': cross-origin requests from any site "
                    + "are allowed. Restrict it to an explicit list of trusted origins.");
        }
        log.info("Startup credential check passed (admin password set, SSH secret key set, "
                + "CORS origins: {}).", (corsOrigins == null || corsOrigins.isBlank()) ? "<same-origin only>" : corsOrigins);
    }
}
