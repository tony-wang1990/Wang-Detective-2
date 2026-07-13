package com.tony.kingdetective.telegram;

import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.factory.CallbackHandlerFactory;
import com.tony.kingdetective.telegram.handler.CallbackHandler;
import com.tony.kingdetective.telegram.service.SshService;
import com.tony.kingdetective.telegram.service.TgAccountFlowService;
import com.tony.kingdetective.telegram.service.TgSessionFlowService;
import com.tony.kingdetective.telegram.storage.SshConnectionStorage;
import com.tony.kingdetective.telegram.storage.ConfigSessionStorage;
import com.tony.kingdetective.telegram.utils.MarkdownFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Telegram Bot 主类
 * 使用命令模式重构的模块化架构
 * 
 * 性能优化：
 * - 所有消息处理使用 Java 21 虚拟线程（Virtual Threads）
 * - 避免阻塞主线程，显著提升响应速度和并发处理能力
 * - 虚拟线程轻量级，可以创建数百万个而不影响性能
 *
 * @author Tony Wang
 */
@Slf4j
public class TgBot implements LongPollingSingleThreadUpdateConsumer {

    private final String BOT_TOKEN;
    private final String CHAT_ID;
    private final TelegramClient telegramClient;
    private final TgAccountFlowService accountFlowService;
    private final TgSessionFlowService sessionFlowService;

    public TgBot(String botToken, String chatId) {
        BOT_TOKEN = botToken;
        CHAT_ID = chatId;
        telegramClient = new OkHttpTelegramClient(BOT_TOKEN);
        accountFlowService = SpringUtil.getBean(TgAccountFlowService.class);
        sessionFlowService = SpringUtil.getBean(TgSessionFlowService.class);
        registerBotCommands();
    }

    private void registerBotCommands() {
        try {
            List<BotCommand> commands = List.of(
                new BotCommand("menu", "唤出探长主菜单"),
                new BotCommand("rescue", "进入救援中心"),
                new BotCommand("terminal", "开启运维终端"),
                new BotCommand("backup", "备份数据归档")
            );
            telegramClient.execute(new SetMyCommands(commands));
            log.info("Successfully registered Telegram bot commands");
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot commands: {}", e.getMessage());
        }
    }

    @Override
    public void consume(List<Update> updates) {
        LongPollingSingleThreadUpdateConsumer.super.consume(updates);
    }

    @Override
    public void consume(Update update) {
        // Use virtual thread to handle all updates asynchronously
        // This prevents blocking and improves bot responsiveness
        Thread.ofVirtual().start(() -> {
            try {
                // 处理文本消息
                if (update.hasMessage() && update.getMessage().hasText()) {
                    handleTextMessage(update);
                    return;
                }

                // 处理文件上传
                if (update.hasMessage() && update.getMessage().hasDocument()) {
                    handleDocumentMessage(update);
                    return;
                }

                // 处理回调查询
                if (update.hasCallbackQuery()) {
                    handleCallbackQuery(update);
                }
            } catch (Exception e) {
                log.error("Error processing update", e);
            }
        });
    }

    /**
     * 处理文本消息（命令和对话）
     */
    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        // 检查权限
        if (!isAuthorized(chatId)) {
            sendUnauthorizedMessage(chatId);
            return;
        }

        // 处理命令（命令优先级最高）
        if (messageText.startsWith("/")) {
            handleCommand(chatId, messageText);
            return;
        }

        // 检查是否正在配置 VNC/备份等（使用新的会话管理）
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        if (configStorage.hasActiveSession(chatId)) {
            ConfigSessionStorage.SessionType sessionType = configStorage.getSessionType(chatId);
            
            if (sessionType == ConfigSessionStorage.SessionType.VNC_CONFIG) {
                handleVncUrlInput(chatId, messageText);
            } else if (sessionType == ConfigSessionStorage.SessionType.BACKUP_PASSWORD) {
                handleBackupPasswordInput(chatId, messageText);
            } else if (sessionType == ConfigSessionStorage.SessionType.RESTORE_PASSWORD) {
                handleRestorePasswordInput(chatId, messageText);
            } else if (sessionType == ConfigSessionStorage.SessionType.ADD_ACCOUNT_CONFIG) {
                handleAddAccountConfigInput(chatId, messageText);
            } else if (sessionType == ConfigSessionStorage.SessionType.ADD_ACCOUNT_KEY) {
                handleAddAccountKeyInput(chatId, messageText);
            } else if (sessionType == ConfigSessionStorage.SessionType.ADD_ACCOUNT_REMARK) {
                handleAddAccountRemarkInput(chatId, messageText);
            }
            return;
        }

        sendMessage(chatId, "请使用 /menu 打开功能菜单，或使用 /help 查看命令。");
    }

    /**
     * 处理命令
     */
    private void handleCommand(long chatId, String command) {
        // Use virtual thread for command handling to avoid blocking
        Thread.ofVirtual().start(() -> {
            try {
                if ("/start".equals(command) || "/menu".equals(command)) {
                    sendMainMenu(chatId);
                } else if ("/cancel".equals(command)) {
                    handleCancelCommand(chatId);
                } else if ("/rescue".equals(command)) {
                    sendRescueMenu(chatId);
                } else if ("/terminal".equals(command)) {
                    sendTerminalMenu(chatId);
                } else if ("/backup".equals(command)) {
                    sendBackupMenu(chatId);
                } else if (command.startsWith("/ssh_config ")) {
                    handleSshConfig(chatId, command);
                } else if (command.startsWith("/ssh ")) {
                    handleSshCommand(chatId, command);
                } else if ("/help".equals(command)) {
                    sendHelpMessage(chatId);
                } else {
                    sendMessage(chatId, "❌ 未知命令，输入 /help 查看帮助");
                }
            } catch (Exception e) {
                log.error("Error handling command: {}", command, e);
                sendMessage(chatId, "❌ 命令处理失败: " + e.getMessage());
            }
        });
    }

    /**
     * 处理取消命令
     */
    private void handleCancelCommand(long chatId) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        
        if (configStorage.hasActiveSession(chatId)) {
            configStorage.clearSession(chatId);
            sendMessage(chatId, "✅ 已取消配置操作");
        } else {
            sendMessage(chatId, "❓ 当前没有进行中的配置操作");
        }
    }

    /**
     * 处理 VNC URL 输入（委托给 TgSessionFlowService）
     */
    private void handleVncUrlInput(long chatId, String url) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        if (!sessionFlowService.isValidVncUrl(url)) {
            sendMessage(chatId,
                "❌ URL 格式错误\n\n必须以 http:// 或 https:// 开头\n\n" +
                "示例：\n• http://192.168.1.100:6080\n• https://vnc.example.com\n\n" +
                "请重新输入或发送 /cancel 取消配置");
            return;
        }
        url = sessionFlowService.normalizeVncUrl(url);
        String error = sessionFlowService.saveVncUrl(url);
        configStorage.clearSession(chatId);
        if (error != null) {
            sendMessage(chatId, "❌ 保存 VNC URL 失败: " + error);
        } else {
            sendMessage(chatId,
                String.format("✅ *VNC URL 配置成功*\n\n配置的 URL: %s\n\n" +
                    "💡 在实例管理中点击 VNC 按钮即可在面板内嵌使用。", url), true);
            log.info("VNC URL configured: chatId={}, url={}", chatId, url);
        }
    }

    /**
     * 处理备份密码输入（委托给 TgSessionFlowService）
     */
    private void handleBackupPasswordInput(long chatId, String password) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        password = password.trim();
        if (!sessionFlowService.isValidBackupPassword(password)) {
            sendMessage(chatId, "❌ 密码太短\n\n建议密码至少 8 位字符\n\n请重新输入或发送 /cancel 取消操作");
            return;
        }
        sendMessage(chatId, "⏳ 正在创建加密备份，请稍候...");
        try {
            String backupFilePath = sessionFlowService.createEncryptedBackup(password);
            java.io.File backupFile = new java.io.File(backupFilePath);
            if (!backupFile.exists()) throw new Exception("备份文件不存在：" + backupFilePath);
            org.telegram.telegrambots.meta.api.methods.send.SendDocument doc =
                org.telegram.telegrambots.meta.api.methods.send.SendDocument.builder()
                    .chatId(chatId)
                    .document(new org.telegram.telegrambots.meta.api.objects.InputFile(backupFile))
                    .caption(MarkdownFormatter.formatMarkdown("📦 *加密备份*\n创建时间：" +
                        java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) +
                        "\n\n恢复时需要输入密码，请妥善保管。"))
                    .parseMode("MarkdownV2").build();
            telegramClient.execute(doc);
            sessionFlowService.deleteBackupFile(backupFilePath);
            configStorage.clearSession(chatId);
            sendMessage(chatId, "✅ *加密备份创建成功*\n\n备份文件已发送，服务器不保留副本。", true);
            log.info("Encrypted backup sent: chatId={}, file={}", chatId, backupFilePath);
        } catch (Exception e) {
            log.error("Failed to create encrypted backup: chatId={}", chatId, e);
            sendMessage(chatId, "❌ 创建加密备份失败: " + e.getMessage());
            configStorage.clearSession(chatId);
        }
    }

    /**
     * 处理恢复密码输入（委托给 TgSessionFlowService）
     */
    private void handleRestorePasswordInput(long chatId, String password) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        ConfigSessionStorage.SessionState state = configStorage.getSessionState(chatId);
        if (state == null || state.getData().get("backupFilePath") == null) {
            sendMessage(chatId, "❌ 会话已过期，请重新上传备份文件");
            configStorage.clearSession(chatId);
            return;
        }
        String backupFilePath = (String) state.getData().get("backupFilePath");
        password = password.trim();
        if (!new java.io.File(backupFilePath).exists()) {
            sendMessage(chatId, "❌ 备份文件不存在，请重新上传备份文件。");
            configStorage.clearSession(chatId);
            return;
        }
        sendMessage(chatId, "⏳ 正在恢复数据，请稍候...\n\n⚠️ 恢复过程中请勿关闭程序！");
        try {
            sessionFlowService.restoreFromBackup(backupFilePath, password);
            configStorage.clearSession(chatId);
            sessionFlowService.deleteBackupFile(backupFilePath);
            sendMessage(chatId,
                "✅ *数据恢复成功*\n\n建议重启服务以确保所有配置生效。", true);
            log.info("Data restored: chatId={}, file={}", chatId, backupFilePath);
        } catch (Exception e) {
            log.error("Restore failed: chatId={}, file={}", chatId, backupFilePath, e);
            sessionFlowService.deleteBackupFile(backupFilePath);
            configStorage.clearSession(chatId);
            sendMessage(chatId,
                "❌ *数据恢复失败*\n\n错误：" + e.getMessage() +
                "\n\n可能原因：密码错误 / 备份文件损坏 / 版本不匹配", true);
        }
    }

    /**
     * 处理文档消息（文件上传）
     */
    private void handleDocumentMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        
        // 检查权限
        if (!isAuthorized(chatId)) {
            sendUnauthorizedMessage(chatId);
            return;
        }
        
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        
        // 检查是否处于恢复模式 或 添加账户模式
        ConfigSessionStorage.SessionType sessionType = configStorage.getSessionType(chatId);
        if (!configStorage.hasActiveSession(chatId) || 
            (sessionType != ConfigSessionStorage.SessionType.RESTORE_PASSWORD && 
             sessionType != ConfigSessionStorage.SessionType.ADD_ACCOUNT_KEY)) {
            sendMessage(chatId, "❌ 请先在相关菜单中发起操作");
            return;
        }
        
        // Handle Account Key Upload
        if (sessionType == ConfigSessionStorage.SessionType.ADD_ACCOUNT_KEY) {
            handleAddAccountKeyFile(update, chatId);
            return;
        }
        
        try {
            org.telegram.telegrambots.meta.api.objects.Document document = update.getMessage().getDocument();
            String fileName = document.getFileName();
            
            // 验证文件类型
            if (!fileName.toLowerCase().endsWith(".zip")) {
                sendMessage(chatId, 
                    "❌ 文件格式错误\n\n" +
                    "只支持 ZIP 格式的备份文件\n\n" +
                    "请重新上传或发送 /cancel 取消操作"
                );
                return;
            }
            
            // Send downloading message
            sendMessage(chatId, "⏳ 正在下载备份文件...\n\n请稍候。");
            
            // Download file from Telegram
            String fileId = document.getFileId();
            org.telegram.telegrambots.meta.api.methods.GetFile getFile = 
                new org.telegram.telegrambots.meta.api.methods.GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File tgFile = telegramClient.execute(getFile);
            
            // Download file to temp directory
            String basicDirPath = System.getProperty("user.dir") + java.io.File.separator;
            String tempFilePath = basicDirPath + "temp_restore_" + System.currentTimeMillis() + ".zip";
            java.io.File localFile = new java.io.File(tempFilePath);
            
            // Download file content
            java.io.File downloadedFile = telegramClient.downloadFile(tgFile);
            
            // Copy to our temp location
            cn.hutool.core.io.FileUtil.copy(downloadedFile, localFile, true);

            log.info("Backup file downloaded: chatId={}, file={}", chatId, tempFilePath);
            
            // Store file path in session
            ConfigSessionStorage.SessionState state = configStorage.getSessionState(chatId);
            if (state != null) {
                state.getData().put("backupFilePath", tempFilePath);
            }
            
            // Ask for password (even for unencrypted backups, we'll try without password first)
            sendMessage(chatId,
                "✅ *文件上传成功*\n\n" +
                "文件名：" + fileName + "\n\n" +
                "请发送解密密码：\n\n" +
                "💡 提示：\n" +
                "• 如果是普通备份，发送任意字符即可\n" +
                "• 如果是加密备份，请输入正确的密码\n" +
                "• 发送 /cancel 可取消操作",
                true
            );
            
        } catch (Exception e) {
            log.error("Failed to handle document upload", e);
            sendMessage(chatId, "❌ 文件上传失败: " + e.getMessage());
            configStorage.clearSession(chatId);
        }
    }

    /**
     * 处理 SSH 配置命令（使用虚拟线程异步处理，避免阻塞）
     */
    private void handleSshConfig(long chatId, String command) {
        try {
            // Format: /ssh_config host port username password
            // Note: password can contain spaces and special characters, so we only split the first 3 parameters
            String configString = command.substring(12).trim();
            
            if (configString.isEmpty()) {
                sendMessage(chatId,
                        "❌ 参数不足\n\n" +
                                "格式: /ssh_config host port username password\n" +
                                "例如: /ssh_config 192.168.1.100 22 root mypassword"
                );
                return;
            }
            
            // Split into maximum 4 parts: host, port, username, and the rest as password
            String[] parts = configString.split("\\s+", 4);

            if (parts.length < 4) {
                sendMessage(chatId,
                        "❌ 参数不足\n\n" +
                                "格式: /ssh_config host port username password\n" +
                                "例如: /ssh_config 192.168.1.100 22 root mypassword\n\n" +
                                "⚠️ 注意：所有4个参数都是必需的"
                );
                return;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            String username = parts[2];
            String password = parts[3]; // Everything after username is treated as password

            // Send testing message immediately
            sendMessage(chatId, "🔄 正在测试连接...");

            // Test connection asynchronously using virtual thread to avoid blocking
            Thread.ofVirtual().start(() -> {
                try {
                    SshService sshService = SpringUtil.getBean(SshService.class);
                    boolean connected = sshService.testConnection(host, port, username, password);
                    
                    if (connected) {
                        SshConnectionStorage.getInstance().saveConnection(chatId, host, port, username, password);
                        sendMessage(chatId,
                                String.format(
                                        "✅ SSH 连接配置成功\n\n" +
                                                "主机: %s:%d\n" +
                                                "用户: %s\n\n" +
                                                "现在可以使用 /ssh [命令] 来执行命令了",
                                        host, port, username
                                )
                        );
                        log.info("SSH connection configured: chatId={}, host={}", chatId, host);
                    } else {
                        sendMessage(chatId, "❌ 连接测试失败，请检查配置是否正确");
                    }
                } catch (Exception e) {
                    log.error("Failed to test SSH connection", e);
                    sendMessage(chatId, "❌ 连接测试失败: " + e.getMessage());
                }
            });

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ 端口号格式错误");
        } catch (Exception e) {
            log.error("Failed to configure SSH", e);
            sendMessage(chatId, "❌ 配置失败: " + e.getMessage());
        }
    }

    /**
     * 处理 SSH 命令执行（异步执行避免阻塞）
     */
    private void handleSshCommand(long chatId, String command) {
        SshConnectionStorage storage = SshConnectionStorage.getInstance();

        if (!storage.hasConnection(chatId)) {
            sendMessage(chatId,
                    "❌ 未配置 SSH 连接\n\n" +
                            "请使用 /ssh_config 命令配置连接信息"
            );
            return;
        }

        try {
            // Get command (remove /ssh prefix)
            String sshCommand = command.substring(5).trim();

            if (sshCommand.isEmpty()) {
                sendMessage(chatId, "❌ 请输入要执行的命令\n\n例如: /ssh ls -la");
                return;
            }

            // Send executing message
            sendMessage(chatId, "⏳ 正在执行命令...");

            // Execute command asynchronously to avoid blocking
            SshConnectionStorage.SshInfo info = storage.getConnection(chatId);
            SshService sshService = SpringUtil.getBean(SshService.class);

            CompletableFuture.supplyAsync(() -> {
                return sshService.executeCommand(
                        info.getHost(),
                        info.getPort(),
                        info.getUsername(),
                        info.getPassword(),
                        sshCommand
                );
            }).thenAccept(result -> {
                // Format and send result (with Markdown enabled for code blocks)
                String formattedResult = sshService.formatOutput(result);
                sendMessage(chatId, formattedResult, true);
                log.info("SSH command executed: chatId={}, command={}", chatId, sshCommand);
            }).exceptionally(ex -> {
                log.error("Failed to execute SSH command", ex);
                sendMessage(chatId, "❌ 执行失败: " + ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to handle SSH command", e);
            sendMessage(chatId, "❌ 处理失败: " + e.getMessage());
        }
    }

    // ==========================================
    // Account Addition Logic
    // ==========================================

    /**
     * 第一步：处理 OCI Config 输入
     */
    private void handleAddAccountConfigInput(long chatId, String text) {
        ConfigSessionStorage storage = ConfigSessionStorage.getInstance();
        try {
            // feat: 委托给 TgAccountFlowService 解析，TgBot 只负责流程路由和消息发送
            Map<String, String> parsed = accountFlowService.parseConfigText(text);

            if (parsed == null) {
                sendMessage(chatId, 
                    "❌ *配置格式错误*\n\n" +
                    "未检测到必要的字段 (user, fingerprint, tenancy, region)。\n" +
                    "请检查复制的内容是否完整。\n\n" +
                    "请重新输入，或发送 /cancel 取消。"
                , true);
                return;
            }
            
            // Transition to next step
            storage.startAddAccountKey(chatId, new java.util.HashMap<>(parsed));
            
            sendMessage(chatId, 
                "✅ *配置已识别*\n\n" +
                "🔑 *第二步：上传私钥*\n\n" +
                "请发送私钥文件 (`.pem`) 或直接发送私钥内容文本。\n\n" +
                "格式示例：\n" +
                "`-----BEGIN PRIVATE KEY-----...`"
            , true);

        } catch (Exception e) {
            log.error("Failed to parse config input", e);
            sendMessage(chatId, "❌ 处理配置失败: " + e.getMessage());
        }
    }

    /**
     * 第二步：处理私钥文本输入
     */
    private void handleAddAccountKeyInput(long chatId, String text) {
        // feat: 委托校验逻辑给 TgAccountFlowService
        if (!accountFlowService.isValidPrivateKey(text)) {
            sendMessage(chatId, "❌ *非法的私钥格式*\n\n请确保包含 `-----BEGIN ... PRIVATE KEY-----` 头。", true);
            return;
        }
        processAccountKey(chatId, text);
    }

    /**
     * 第二步：处理私钥文件上传
     */
    private void handleAddAccountKeyFile(Update update, long chatId) {
        try {
            org.telegram.telegrambots.meta.api.objects.Document document = update.getMessage().getDocument();
            String fileId = document.getFileId();
            org.telegram.telegrambots.meta.api.methods.GetFile getFile = new org.telegram.telegrambots.meta.api.methods.GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File tgFile = telegramClient.execute(getFile);
            java.io.File downloadedFile = telegramClient.downloadFile(tgFile);
            String keyContent = cn.hutool.core.io.FileUtil.readUtf8String(downloadedFile);
            
            // feat: 委托校验逻辑给 TgAccountFlowService
            if (!accountFlowService.isValidPrivateKey(keyContent)) {
                sendMessage(chatId, "❌ *文件无效*\n\n文件内容不是有效的私钥格式。", true);
                return;
            }
            processAccountKey(chatId, keyContent);
        } catch (Exception e) {
            log.error("Failed to handle key file", e);
            sendMessage(chatId, "❌ 读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 处理私钥通用逻辑 (保存并进入下一步)
     */
    private void processAccountKey(long chatId, String keyContent) {
        ConfigSessionStorage storage = ConfigSessionStorage.getInstance();
        ConfigSessionStorage.SessionState state = storage.getSessionState(chatId);
        
        if (state == null) {
            sendMessage(chatId, "❌ 会话已过期，请重新开始。");
            return;
        }

        // Save key content to session data temporarily
        state.getData().put("keyContent", keyContent);
        
        // Transition to next step
        storage.startAddAccountRemark(chatId, state.getData());
        
        sendMessage(chatId, 
            "✅ *私钥已接收*\n\n" +
            "🏷️ *第三步：设置备注名*\n\n" +
            "给这个账户起个名字（例如：`US-SanJose` 或 `我的甲骨文1号`）。\n" +
            "这将用于在菜单中显示。"
        , true);
    }

    /**
     * 第三步：处理备注输入并完成添加
     */
    private void handleAddAccountRemarkInput(long chatId, String remark) {
        ConfigSessionStorage storage = ConfigSessionStorage.getInstance();
        ConfigSessionStorage.SessionState state = storage.getSessionState(chatId);
        
        if (state == null) {
            sendMessage(chatId, "❌ 会话已过期，请重新开始。");
            return;
        }
        
        try {
            sendMessage(chatId, "⏳ 正在验证并保存...");
            
            Map<String, Object> data = state.getData();
            String userOctId   = (String) data.get("user");
            String fingerprint = (String) data.get("fingerprint");
            String tenancy     = (String) data.get("tenancy");
            String region      = (String) data.get("region");
            String keyContent  = (String) data.get("keyContent");
            
            sendMessage(chatId, "⏳ 正在验证 OCI API 连通性...");

            // feat: 全部数据操作委托给 TgAccountFlowService，TgBot 只处理消息
            String errorMsg = accountFlowService.saveAndVerify(remark, userOctId, fingerprint, tenancy, region, keyContent);

            if (errorMsg != null) {
                storage.clearSession(chatId);
                sendMessage(chatId,
                    "❌ *账户添加失败 - OCI 连通性验证不通过*\n\n" +
                    "错误信息：" + errorMsg + "\n\n" +
                    "💡 可能原因：\n" +
                    "• API Key 与账户不匹配（指纹错误）\n" +
                    "• 私钥文件内容不完整或格式错误\n" +
                    "• 区域（Region）填写错误\n" +
                    "• OCI 账户权限不足\n\n" +
                    "请检查后重新发送 /start 再试。",
                    true
                );
                return;
            }

            storage.clearSession(chatId);
            sendMessage(chatId,
                String.format("🎉 *账户添加成功！*\n\n" +
                              "备注名: %s\n" +
                              "区域: %s\n" +
                              "状态: ✅ 已验证 OCI 连通性\n\n" +
                              "您可以点击下方按钮管理该账户。", remark, region),
                true
            );

        } catch (Exception e) {
            log.error("Failed to save new account", e);
            sendMessage(chatId, "❌ 保存失败: " + e.getMessage());
            storage.clearSession(chatId);
        }
    }

    /**
     * getValueFromConfig 委托给 TgAccountFlowService
     * 保留为 private 以兼容其他可能的调用点
     */
    private String getValueFromConfig(String text, String key) {
        return accountFlowService.getValueFromConfig(text, key);
    }

    /**
     * 发送帮助消息
     */
    private void sendHelpMessage(long chatId) {
        String helpText =
                "📖 *命令帮助*\n\n" +
                        "*基础命令：*\n" +
                        "├ `/start` - 显示主菜单\n" +
                        "├ `/help` - 显示此帮助信息\n\n" +
                        "*运维入口：*\n" +
                        "├ `/terminal` - 打开 SSH/日志/诊断快捷入口\n" +
                        "├ `/rescue` - 打开救援中心\n" +
                        "├ `/backup` - 打开备份恢复\n\n" +
                        "*SSH 管理：*\n" +
                        "├ `/ssh_config host port user pwd` - 配置连接\n" +
                        "├ `/ssh 命令` - 执行 SSH 命令\n" +
                        "└ 示例: `/ssh ls -la`\n\n" +
                        "💡 更多功能请点击 /start 查看主菜单";

        sendMessage(chatId, helpText, true);
    }

    /**
     * 使用处理器工厂处理回调查询（使用虚拟线程避免阻塞）
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        String callbackQueryId = update.getCallbackQuery().getId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        log.info("Handling callback query: callbackData={}, chatId={}", callbackData, chatId);

        // 检查权限
        if (!isAuthorized(chatId)) {
            log.warn("Unauthorized callback attempt: chatId={}", chatId);
            answerCallbackQuery(callbackQueryId, "无权限操作");
            sendUnauthorizedMessage(chatId);
            return;
        }

        answerCallbackQuery(callbackQueryId, "请求已接收，正在处理");

        // Use virtual thread to handle callback asynchronously
        Thread.ofVirtual().start(() -> {
            try {
                CallbackHandlerFactory factory = SpringUtil.getBean(CallbackHandlerFactory.class);
                CallbackHandler handler = factory.getHandler(callbackData).orElse(null);

                if (handler != null) {
                    log.info("Found handler for callback: handler={}, callbackData={}", 
                        handler.getClass().getSimpleName(), callbackData);
                    
                    BotApiMethod<? extends Serializable> response = handler.handle(
                            update.getCallbackQuery(),
                            telegramClient
                    );

                    if (response != null) {
                        log.debug("Executing response from handler: responseType={}", response.getClass().getSimpleName());
                        executeCallbackResponse(response, callbackData);
                        log.info("Successfully executed callback response: callbackData={}", callbackData);
                    } else {
                        // Some handlers execute TelegramClient directly and intentionally return null.
                        log.debug("Handler completed without a returned BotApiMethod: handler={}, callbackData={}",
                            handler.getClass().getSimpleName(), callbackData);
                    }
                } else {
                    log.warn("未找到处理回调数据的处理器: callbackData={}", callbackData);
                    answerCallbackQuery(callbackQueryId, "这个菜单暂未接入处理器");
                    sendMessage(chatId, "⚠️ 这个菜单暂未接入处理器，请返回主菜单重试。");
                }
            } catch (TelegramApiException e) {
                String errorId = shortErrorId();
                log.error("处理回调查询失败: errorId={}, callbackData={}, error={}",
                        errorId, callbackData, e.getMessage(), e);
                try {
                    answerCallbackQuery(callbackQueryId, "菜单处理失败");
                    sendMessage(chatId, String.format(
                            "❌ 菜单处理失败\n\n功能：%s\n错误编号：%s\n请返回主菜单重试；如持续失败，请在服务日志中搜索该编号。",
                            callbackLabel(callbackData), errorId));
                } catch (Exception ex) {
                    log.error("Failed to send error message to user", ex);
                }
            } catch (Exception e) {
                String errorId = shortErrorId();
                log.error("处理回调时发生意外错误: errorId={}, callbackData={}, errorType={}, message={}",
                        errorId, callbackData, e.getClass().getSimpleName(), e.getMessage(), e);
                try {
                    answerCallbackQuery(callbackQueryId, "系统处理异常");
                    sendMessage(chatId, String.format(
                            "❌ 系统处理异常\n\n功能：%s\n错误编号：%s\n请重试；如持续失败，请在服务日志中搜索该编号。",
                            callbackLabel(callbackData), errorId));
                } catch (Exception ex) {
                    log.error("Failed to send error message to user", ex);
                }
            }
        });
    }

    private String shortErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String callbackLabel(String callbackData) {
        if (callbackData == null || callbackData.isBlank()) {
            return "unknown";
        }
        String normalized = callbackData.replaceAll("[^a-zA-Z0-9_:-]", "");
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private void executeCallbackResponse(BotApiMethod<? extends Serializable> response, String callbackData) throws TelegramApiException {
        try {
            telegramClient.execute(response);
        } catch (TelegramApiException e) {
            if (response instanceof AnswerCallbackQuery && isCallbackAlreadyAnswered(e)) {
                log.debug("Ignored duplicate callback answer: callbackData={}, message={}", callbackData, e.getMessage());
                return;
            }
            if (isMarkdownParseError(e) && retryCallbackResponseAsPlainText(response, callbackData)) {
                return;
            }
            throw e;
        }
    }

    private boolean retryCallbackResponseAsPlainText(BotApiMethod<? extends Serializable> response, String callbackData) {
        try {
            if (response instanceof EditMessageText editMessageText) {
                editMessageText.setParseMode(null);
                telegramClient.execute(editMessageText);
                log.warn("Retried callback response as plain text after Markdown parse failure: callbackData={}", callbackData);
                return true;
            }
            if (response instanceof SendMessage sendMessage) {
                sendMessage.setParseMode(null);
                telegramClient.execute(sendMessage);
                log.warn("Retried callback send message as plain text after Markdown parse failure: callbackData={}", callbackData);
                return true;
            }
        } catch (Exception retryError) {
            log.error("Retrying callback response as plain text failed: callbackData={}", callbackData, retryError);
        }
        return false;
    }

    private boolean isMarkdownParseError(TelegramApiException e) {
        String message = e.getMessage();
        return message != null && message.contains("can't parse entities");
    }

    private boolean isCallbackAlreadyAnswered(TelegramApiException e) {
        String message = e.getMessage();
        return message != null && (message.contains("query is too old") || message.contains("query ID is invalid"));
    }

    private void answerCallbackQuery(String callbackQueryId, String text) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            if (!isCallbackAlreadyAnswered(e)) {
                log.debug("Answer callback query failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查用户是否有权限
     * feat #18: 支持多 Chat ID，CHAT_ID 可配置为逗号分隔的多个ID
     * 例如: TELEGRAM_CHAT_ID=123456,789012,345678
     */
    private boolean isAuthorized(long chatId) {
        if (CHAT_ID == null || CHAT_ID.isBlank()) return false;
        String incomingId = String.valueOf(chatId);
        for (String id : CHAT_ID.split(",")) {
            if (id.trim().equals(incomingId)) return true;
        }
        return false;
    }

    /**
     * 发送无权限消息
     */
    private void sendUnauthorizedMessage(long chatId) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ 无权限操作此机器人🤖，项目地址：https://github.com/tony-wang1990/Wang-Detective-2")
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送无权限消息失败", e);
        }
    }

    /**
     * 发送主菜单
     */
    private void sendMainMenu(long chatId) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MarkdownFormatter.formatMarkdown("🕵️ *W-探长* 主菜单："))
                    .parseMode("MarkdownV2")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(KeyboardBuilder.buildMainMenu())
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送主菜单失败", e);
        }
    }

    private void sendTerminalMenu(long chatId) {
        try {
            String text = "🧰 *运维终端*\n\n" +
                    "这里提供 Telegram 侧的常用运维入口。Web SSH/SFTP 完整交互仍在 Web 面板内执行，Bot 侧可快速查看主机、日志、诊断和任务状态。\n\n" +
                    "请选择要打开的入口：";

            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow> keyboard = new java.util.ArrayList<>();
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("SSH 管理", "ssh_management"),
                    KeyboardBuilder.button("主机概览", "ops_host_list")
            ));
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("最近日志", "ops_recent_logs"),
                    KeyboardBuilder.button("错误日志", "ops_error_logs")
            ));
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("系统诊断", "ops_diagnostics"),
                    KeyboardBuilder.button("任务状态", "ops_task_status")
            ));
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("运维中心", "ops_center")
            ));
            keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
            keyboard.add(KeyboardBuilder.buildCancelRow());

            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MarkdownFormatter.formatMarkdown(text))
                    .parseMode("MarkdownV2")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(keyboard)
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送运维终端菜单失败", e);
        }
    }

    private void sendRescueMenu(long chatId) {
        try {
            String text = "🆘 *救援中心*\n\n" +
                    "面向 OCI 实例失联、SSH 异常、Boot Volume 修复的应急操作向导。\n\n" +
                    "⚠️ *高危操作说明*\n" +
                    "• 自动救援会创建临时救援实例并挂载目标卷\n" +
                    "• 操作前请确认目标实例数据已备份\n" +
                    "• 救援完成后请手动检查数据完整性\n\n" +
                    "📖 *可用操作*\n" +
                    "• 自动救援 — 自动化拆卷救援流程\n" +
                    "• 救援指南 — 查看当前救援状态和文档\n\n" +
                    "请选择操作：";
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow> keyboard = new java.util.ArrayList<>();
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("🚑 发起自动救援", "rescue_choose_account"),
                    KeyboardBuilder.button("📋 救援指南", "rescue_guide")
            ));
            keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
            keyboard.add(KeyboardBuilder.buildCancelRow());

            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MarkdownFormatter.formatMarkdown(text))
                    .parseMode("MarkdownV2")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(keyboard)
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送救援菜单失败", e);
        }
    }

    private void sendBackupMenu(long chatId) {
        try {
            String text = "📦 *备份与恢复*\n\n" +
                    "💾 功能说明：\n" +
                    "• 备份：导出系统配置和数据\n" +
                    "• 恢复：从备份文件恢复系统\n\n" +
                    "📝 备份内容包括：\n" +
                    "• OCI 配置信息\n" +
                    "• 系统设置\n" +
                    "• 任务配置\n" +
                    "• 其他重要数据\n\n" +
                    "🔒 安全选项：\n" +
                    "• 支持加密备份（推荐）\n" +
                    "• 保护敏感信息安全\n\n" +
                    "⚠️ 注意：\n" +
                    "• 恢复操作会覆盖现有数据\n" +
                    "• 建议定期备份重要数据\n" +
                    "• 请妥善保管备份文件\n\n" +
                    "⚙️ 请选择操作：";

            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow> keyboard = new java.util.ArrayList<>();
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("💾 创建备份", "backup_create")
            ));
            keyboard.add(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                    KeyboardBuilder.button("📥 恢复数据", "restore_data")
            ));
            keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
            keyboard.add(KeyboardBuilder.buildCancelRow());

            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MarkdownFormatter.formatMarkdown(text))
                    .parseMode("MarkdownV2")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(keyboard)
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送备份菜单失败", e);
        }
    }

    /**
     * 发送普通消息
     *
     * @param chatId         chat ID
     * @param text           message text
     * @param enableMarkdown whether to enable Markdown parsing
     */
    private void sendMessage(long chatId, String text, boolean enableMarkdown) {
        try {
            String finalText;
            if (enableMarkdown) {
                finalText = MarkdownFormatter.truncate(MarkdownFormatter.formatMarkdown(text));
            } else {
                finalText = MarkdownFormatter.truncate(text);
            }

            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId)
                    .text(finalText);

            if (enableMarkdown) {
                builder.parseMode("MarkdownV2");
            }

            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("发送消息失败 (enableMarkdown={}): {}", enableMarkdown, e.getMessage());
            // Fallback：纯文本重试
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(MarkdownFormatter.truncate(text))
                        .build());
                log.info("消息 fallback 纯文本发送成功");
            } catch (TelegramApiException fallbackEx) {
                log.error("消息 fallback 发送也失败", fallbackEx);
            }
        }
    }

    /**
     * 发送普通消息（默认不启用 Markdown）
     */
    private void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, false);
    }
}
