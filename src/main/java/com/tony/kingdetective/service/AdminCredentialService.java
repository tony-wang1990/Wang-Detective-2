package com.tony.kingdetective.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class AdminCredentialService {

    private static final String ADMIN_CREDENTIAL_TYPE = "Y004";
    private static final String ADMIN_ACCOUNT_CODE = "SYS_WEB_ACCOUNT";
    private static final String ADMIN_PASSWORD_CODE = "SYS_WEB_PASSWORD";
    private static final String ADMIN_TOKEN_SECRET_CODE = "SYS_WEB_TOKEN_SECRET";
    private static final String HASH_PREFIX = "pbkdf2$";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_HASH_BITS = 256;

    private final IOciKvService kvService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${web.account:admin}")
    private String defaultAccount;

    // No built-in default password: web.password resolves from ADMIN_PASSWORD /
    // WEB_PASSWORD (see application.yml). If it is blank, StartupCredentialValidator
    // refuses to start the application, so this empty default is never usable.
    @Value("${web.password:}")
    private String defaultPassword;

    public AdminCredentialService(IOciKvService kvService) {
        this.kvService = kvService;
    }

    public String getAccount() {
        return StrUtil.blankToDefault(readValue(ADMIN_ACCOUNT_CODE), defaultAccount);
    }

    public boolean matches(String account, String password) {
        if (!StrUtil.equals(account, getAccount())) {
            return false;
        }
        // Never accept a login when no password is configured at all: without this
        // guard an empty stored password would match an empty login password.
        String stored = passwordValue();
        if (StrUtil.isBlank(stored) || StrUtil.isBlank(password)) {
            return false;
        }
        if (isPasswordHash(stored)) {
            return verifyPassword(password, stored);
        }
        boolean matched = StrUtil.equals(password, stored);
        if (matched && StrUtil.isNotBlank(password)) {
            saveValue(ADMIN_PASSWORD_CODE, hashPassword(password));
        }
        return matched;
    }

    public String generateToken(Map<String, Object> payload) {
        return CommonUtils.genToken(payload, tokenSecret());
    }

    public boolean verifyToken(String token) {
        String secret = tokenSecret();
        return StrUtil.isNotBlank(token)
                && StrUtil.isNotBlank(secret)
                && !CommonUtils.isTokenExpired(token)
                && JWTUtil.verify(token, secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Legacy detector: reports whether the password stored in the database is still the
     * well-known weak default that shipped in versions before 2026-11-24. It is only used
     * by SystemDiagnosticsService to warn existing deployments; new installs can never
     * obtain that password because there is no longer any default (see
     * StartupCredentialValidator). The literal below is a detection constant, not a
     * configured default, and must not be reintroduced as one.
     */
    public boolean isUsingDefaultCredential() {
        String storedPassword = passwordValue();
        return StrUtil.equals("admin", getAccount())
                && (isPasswordHash(storedPassword)
                ? verifyPassword("admin123456", storedPassword)
                : StrUtil.equals("admin123456", storedPassword));
    }

    public boolean hasPlainTextStoredPassword() {
        String storedPassword = readValue(ADMIN_PASSWORD_CODE);
        return StrUtil.isNotBlank(storedPassword) && !isPasswordHash(storedPassword);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCredential(String currentPassword, String newAccount, String newPassword, String confirmPassword) {
        if (!verifyCurrentPassword(currentPassword)) {
            throw new OciException(-1, "当前密码不正确");
        }
        if (StrUtil.isBlank(newAccount)) {
            throw new OciException(-1, "新登录账号不能为空");
        }
        if (StrUtil.isBlank(newPassword) || newPassword.length() < 8) {
            throw new OciException(-1, "新密码至少需要 8 位");
        }
        if (!StrUtil.equals(newPassword, confirmPassword)) {
            throw new OciException(-1, "两次输入的新密码不一致");
        }
        saveValue(ADMIN_ACCOUNT_CODE, newAccount.trim());
        saveValue(ADMIN_PASSWORD_CODE, hashPassword(newPassword));
        rotateTokenSecret();
    }

    private boolean verifyCurrentPassword(String password) {
        String stored = passwordValue();
        return isPasswordHash(stored) ? verifyPassword(password, stored) : StrUtil.equals(password, stored);
    }

    private String passwordValue() {
        return StrUtil.blankToDefault(readValue(ADMIN_PASSWORD_CODE), defaultPassword);
    }

    private synchronized String tokenSecret() {
        String secret = readValue(ADMIN_TOKEN_SECRET_CODE);
        if (StrUtil.isNotBlank(secret)) {
            return secret;
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        saveValue(ADMIN_TOKEN_SECRET_CODE, secret);
        return secret;
    }

    private void rotateTokenSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        saveValue(ADMIN_TOKEN_SECRET_CODE,
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    private boolean isPasswordHash(String value) {
        return StrUtil.isNotBlank(value) && value.startsWith(HASH_PREFIX);
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, PBKDF2_ITERATIONS);
        return HASH_PREFIX
                + PBKDF2_ITERATIONS + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String storedHash) {
        if (StrUtil.isBlank(password) || StrUtil.isBlank(storedHash)) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new OciException(-1, "密码哈希处理失败");
        }
    }

    private String readValue(String code) {
        OciKv kv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, code)
                .orderByDesc(OciKv::getCreateTime)
                .last("limit 1"));
        return kv == null ? null : kv.getValue();
    }

    private void saveValue(String code, String value) {
        kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, code));
        kvService.save(OciKv.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .code(code)
                .type(ADMIN_CREDENTIAL_TYPE)
                .value(value)
                .createTime(LocalDateTime.now())
                .build());
    }
}
