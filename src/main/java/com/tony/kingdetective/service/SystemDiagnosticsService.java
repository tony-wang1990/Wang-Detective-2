package com.tony.kingdetective.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.bean.vo.SystemDiagnostics;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared deployment and runtime diagnostics for Web UI and Telegram Bot.
 */
@Service
public class SystemDiagnosticsService {

    private final DataSource dataSource;
    private final IOciKvService kvService;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${oci-cfg.key-dir-path:}")
    private String keyDirPath;

    @Value("${web.account:}")
    private String adminAccount;

    @Value("${web.password:}")
    private String adminPassword;

    @Value("${telegram.bot.token:${TELEGRAM_BOT_TOKEN:${BOT_TOKEN:}}}")
    private String telegramToken;
    @Value("${telegram.bot.chat-id:${TELEGRAM_BOT_CHAT_ID:${TELEGRAM_CHAT_ID:${TG_CHAT_ID:}}}}")
    private String telegramChatId;

    public SystemDiagnosticsService(DataSource dataSource, IOciKvService kvService) {
        this.dataSource = dataSource;
        this.kvService = kvService;
    }

    public SystemDiagnostics diagnostics() {
        Runtime runtime = Runtime.getRuntime();
        List<SystemDiagnostics.CheckItem> checks = new ArrayList<>();

        Path databasePath = resolveSqlitePath(datasourceUrl);
        Path keyPath = keyDirPath == null || keyDirPath.isBlank() ? null : Path.of(keyDirPath);
        Path logPath = Path.of(CommonUtils.LOG_FILE_PATH);
        Path dataRoot = databasePath == null ? Path.of(".").toAbsolutePath() : databasePath.toAbsolutePath().getParent();
        if (dataRoot == null) {
            dataRoot = Path.of(".").toAbsolutePath();
        }
        File root = dataRoot.toFile();

        checks.add(checkDatabase());
        checks.add(checkPath("database-file", databasePath, false, "SQLite 数据库文件"));
        checks.add(checkPath("key-directory", keyPath, true, "OCI 私钥目录"));
        checks.add(checkPath("log-file", logPath, false, "实时日志文件"));
        checks.add(checkWritable("data-directory", root.toPath()));
        checks.add(checkDefaultPassword());
        checks.add(checkTelegramBot());

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        boolean hasError = checks.stream().anyMatch(item -> "ERROR".equals(item.getStatus()));
        boolean hasWarn = checks.stream().anyMatch(item -> "WARN".equals(item.getStatus()));

        return SystemDiagnostics.builder()
                .status(hasError ? "ERROR" : hasWarn ? "WARN" : "OK")
                .version(CommonUtils.getCurrentVersion())
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .usedMemoryBytes(usedMemory)
                .maxMemoryBytes(runtime.maxMemory())
                .freeDiskBytes(root.getFreeSpace())
                .databasePath(databasePath == null ? null : databasePath.toString())
                .databaseBytes(fileSize(databasePath))
                .keyDirPath(keyDirPath)
                .logFilePath(CommonUtils.LOG_FILE_PATH)
                .checks(checks)
                .build();
    }

    private SystemDiagnostics.CheckItem checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(5);
            return item("database-connectivity", valid ? "OK" : "ERROR",
                    valid ? "数据库连接正常" : "数据库连接不可用");
        } catch (Exception e) {
            return item("database-connectivity", "ERROR", "数据库连接失败: " + e.getMessage());
        }
    }

    private SystemDiagnostics.CheckItem checkPath(String name, Path path, boolean directory, String label) {
        if (path == null) {
            return item(name, "WARN", label + "未配置");
        }
        if (!Files.exists(path)) {
            return item(name, "WARN", label + "不存在: " + path);
        }
        boolean typeOk = directory ? Files.isDirectory(path) : Files.isRegularFile(path);
        return item(name, typeOk ? "OK" : "ERROR", label + (typeOk ? "正常: " : "类型不正确: ") + path);
    }

    private SystemDiagnostics.CheckItem checkWritable(String name, Path path) {
        if (path == null || !Files.exists(path)) {
            return item(name, "WARN", "数据目录不存在");
        }
        return item(name, Files.isWritable(path) ? "OK" : "ERROR",
                Files.isWritable(path) ? "数据目录可写" : "数据目录不可写: " + path);
    }

    private SystemDiagnostics.CheckItem checkDefaultPassword() {
        if ("admin".equals(adminAccount) && "admin123456".equals(adminPassword)) {
            return item("admin-password", "WARN", "仍在使用默认管理员账号密码，建议立即修改");
        }
        if (adminPassword == null || adminPassword.length() < 10) {
            return item("admin-password", "WARN", "管理员密码长度偏短，建议至少 10 位");
        }
        return item("admin-password", "OK", "管理员密码已自定义");
    }

    private SystemDiagnostics.CheckItem checkTelegramBot() {
        String token = firstNonBlank(telegramToken, getCfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN));
        String chatId = firstNonBlank(telegramChatId, getCfgValue(SysCfgEnum.SYS_TG_CHAT_ID));
        if (isBlank(token) && isBlank(chatId)) {
            return item("telegram-bot", "WARN", "Telegram Bot 未配置，相关能力不可用");
        }
        if (isBlank(token) || isBlank(chatId)) {
            return item("telegram-bot", "WARN", "Telegram Bot 配置不完整，需要同时配置 Token 和 Chat ID");
        }
        return item("telegram-bot", "OK", "Telegram Bot Token 和 Chat ID 已配置");
    }

    private String getCfgValue(SysCfgEnum cfg) {
        try {
            OciKv kv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, cfg.getCode())
                    .eq(OciKv::getType, cfg.getType().getCode())
                    .last("LIMIT 1"));
            return kv == null ? null : kv.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private SystemDiagnostics.CheckItem item(String name, String status, String message) {
        return SystemDiagnostics.CheckItem.builder()
                .name(name)
                .status(status)
                .message(message)
                .build();
    }

    private Path resolveSqlitePath(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            return null;
        }
        String path = url.substring("jdbc:sqlite:".length());
        if (path.isBlank() || ":memory:".equals(path)) {
            return null;
        }
        return Path.of(path);
    }

    private Long fileSize(Path path) {
        try {
            return path == null || !Files.exists(path) ? 0L : Files.size(path);
        } catch (Exception e) {
            return 0L;
        }
    }
}
