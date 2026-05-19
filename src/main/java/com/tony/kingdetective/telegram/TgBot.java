package com.tony.kingdetective.telegram;

import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.factory.CallbackHandlerFactory;
import com.tony.kingdetective.telegram.handler.CallbackHandler;
import com.tony.kingdetective.telegram.service.AiChatService;
import com.tony.kingdetective.telegram.service.SshService;
import com.tony.kingdetective.telegram.storage.SshConnectionStorage;
import com.tony.kingdetective.telegram.storage.ConfigSessionStorage;
import com.tony.kingdetective.telegram.utils.MarkdownFormatter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import org.springframework.beans.factory.annotation.Value;
import java.io.Serializable;
import java.util.List;
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
    
    @Value("${oci-cfg.key-dir-path}")
    private String keyDirPath;

    public TgBot(String botToken, String chatId) {
        BOT_TOKEN = botToken;
        CHAT_ID = chatId;
        telegramClient = new OkHttpTelegramClient(BOT_TOKEN);
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

        // 非命令消息，当作 AI 对话处理（已在内部使用异步）
        handleAiChat(chatId, messageText);
    }

    /**
     * 处理命令
     */
    private void handleCommand(long chatId, String command) {
        // Use virtual thread for command handling to avoid blocking
        Thread.ofVirtual().start(() -> {
            try {
                if ("/start".equals(command)) {
                    sendMainMenu(chatId);
                } else if ("/cancel".equals(command)) {
                    handleCancelCommand(chatId);
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
     * 处理 VNC URL 输入
     */
    private void handleVncUrlInput(long chatId, String url) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        
        try {
            // Validate URL format
            url = url.trim();
            
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                sendMessage(chatId, 
                    "❌ URL 格式错误\n\n" +
                    "必须以 http:// 或 https:// 开头\n\n" +
                    "示例：\n" +
                    "• http://192.168.1.100:6080\n" +
                    "• https://vnc.example.com\n\n" +
                    "请重新输入或发送 /cancel 取消配置"
                );
                return;
            }
            
            // Remove trailing slash
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            
            // Save to database
            com.tony.kingdetective.service.IOciKvService kvService = 
                SpringUtil.getBean(com.tony.kingdetective.service.IOciKvService.class);
            
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.tony.kingdetective.bean.entity.OciKv> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(com.tony.kingdetective.bean.entity.OciKv::getCode, 
                com.tony.kingdetective.enums.SysCfgEnum.SYS_VNC.getCode());
            
            com.tony.kingdetective.bean.entity.OciKv vncConfig = kvService.getOne(wrapper);
            
            if (vncConfig != null) {
                // Update existing
                vncConfig.setValue(url);
                kvService.updateById(vncConfig);
            } else {
                // Create new
                vncConfig = new com.tony.kingdetective.bean.entity.OciKv();
                vncConfig.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr());
                vncConfig.setCode(com.tony.kingdetective.enums.SysCfgEnum.SYS_VNC.getCode());
                vncConfig.setValue(url);
                vncConfig.setType(com.tony.kingdetective.enums.SysCfgTypeEnum.SYS_INIT_CFG.getCode());
                kvService.save(vncConfig);
            }
            
            // Stop configuring
            configStorage.clearSession(chatId);
            
            // Send success message
            sendMessage(chatId,
                String.format(
                    "✅ *VNC URL 配置成功*\n\n" +
                    "配置的 URL: %s\n\n" +
                    "💡 使用说明：\n" +
                    "在实例管理中选择单个实例，\n" +
                    "点击 \"开启VNC连接\" 按钮即可使用此 URL。\n\n" +
                    "⚠️ 注意：\n" +
                    "• 请确保 VNC 代理服务已正确配置\n" +
                    "• 确保相应端口已开放或配置了反向代理",
                    url
                ),
                true
            );
            
            log.info("VNC URL configured: chatId={}, url={}", chatId, url);
            
        } catch (Exception e) {
            log.error("Failed to save VNC URL", e);
            sendMessage(chatId, "❌ 保存 VNC URL 失败: " + e.getMessage());
            configStorage.clearSession(chatId);
        }
    }

    /**
     * 处理备份密码输入
     */
    private void handleBackupPasswordInput(long chatId, String password) {
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        
        try {
            // Validate password
            password = password.trim();
            
            if (password.length() < 6) {
                sendMessage(chatId,
                    "❌ 密码太短\n\n" +
                    "建议密码至少 8 位字符\n\n" +
                    "请重新输入或发送 /cancel 取消操作"
                );
                return;
            }
            
            // Send processing message
            sendMessage(chatId, "⏳ 正在创建加密备份...\n\n请稍候，这可能需要几秒钟。");
            
            // Execute encrypted backup using the new method
            com.tony.kingdetective.service.ISysService sysService = 
                SpringUtil.getBean(com.tony.kingdetective.service.ISysService.class);
            
            com.tony.kingdetective.bean.params.sys.BackupParams params = 
                new com.tony.kingdetective.bean.params.sys.BackupParams();
            params.setEnableEnc(true);
            params.setPassword(password);
            
            String backupFilePath = sysService.createBackupFile(params);
            
            log.info("Encrypted backup created: chatId={}, file={}", chatId, backupFilePath);
            
            // Send backup file via Telegram
            java.io.File backupFile = new java.io.File(backupFilePath);
            if (backupFile.exists()) {
                org.telegram.telegrambots.meta.api.methods.send.SendDocument sendDocument = 
                    org.telegram.telegrambots.meta.api.methods.send.SendDocument.builder()
                        .chatId(chatId)
                        .document(new org.telegram.telegrambots.meta.api.objects.InputFile(backupFile))
                        .caption(
                            "📦 *备份文件*\n\n" +
                            "✅ 备份类型：加密备份\n" +
                            "📅 创建时间：" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n" +
                            "💡 说明：\n" +
                            "• 此备份文件已加密\n" +
                            "• 恢复时需要输入密码\n" +
                            "• 请妥善保管密码和文件\n\n" +
                            "⚠️ 重要：\n" +
                            "• 文件已发送到聊天窗口\n" +
                            "• 服务器副本将在发送后删除\n" +
                            "• 请牢记您设置的密码"
                        )
                        .parseMode("Markdown")
                        .build();
                
                try {
                    telegramClient.execute(sendDocument);
                    log.info("Encrypted backup file sent to chatId: {}", chatId);
                    
                    // Delete backup file from server after sending
                    cn.hutool.core.io.FileUtil.del(backupFile);
                    log.info("Backup file deleted from server: {}", backupFilePath);
                    
                    // Send success message
                    sendMessage(chatId,
                        "✅ *加密备份创建成功*\n\n" +
                        "备份文件已发送到聊天窗口。\n\n" +
                        "💡 提示：\n" +
                        "• 请保存备份文件到安全位置\n" +
                        "• 服务器不会保留备份副本\n" +
                        "• 需要时可随时创建新备份\n" +
                        "• 请务必记住您的密码",
                        true
                    );
                    
                } catch (Exception e) {
                    log.error("Failed to send encrypted backup file", e);
                    throw new Exception("发送备份文件失败：" + e.getMessage());
                }
            } else {
                throw new Exception("备份文件不存在：" + backupFilePath);
            }
            
            // Clean up session
            configStorage.clearSession(chatId);
            
        } catch (Exception e) {
            log.error("Failed to create encrypted backup", e);
            sendMessage(chatId, "❌ 创建加密备份失败: " + e.getMessage());
            configStorage.clearSession(chatId);
        }
    }

    /**
     * 处理恢复密码输入
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
        
        // 验证文件是否存在
        java.io.File backupFile = new java.io.File(backupFilePath);
        if (!backupFile.exists()) {
            log.error("Backup file not found: {}", backupFilePath);
            sendMessage(chatId, 
                "❌ 备份文件不存在\n\n" +
                "文件可能已被删除或移动。\n" +
                "请重新上传备份文件。"
            );
            configStorage.clearSession(chatId);
            return;
        }
        
        try {
            // Send processing message
            sendMessage(chatId, "⏳ 正在恢复数据...\n\n请稍候，这可能需要几分钟。\n\n⚠️ 恢复过程中请勿关闭程序！");
            
            // Execute restore
            com.tony.kingdetective.service.ISysService sysService = 
                SpringUtil.getBean(com.tony.kingdetective.service.ISysService.class);
            
            // Try with password, if it fails and password is simple, try without password
            try {
                log.info("Attempting restore with password: chatId={}, file={}", chatId, backupFilePath);
                sysService.recoverFromFile(backupFilePath, password);
            } catch (Exception e) {
                // If password is empty or very simple, try without password
                if (password.isEmpty() || password.length() < 3) {
                    log.info("Retrying restore without password: chatId={}", chatId);
                    sysService.recoverFromFile(backupFilePath, "");
                } else {
                    throw e;
                }
            }
            
            log.info("Data restored successfully: chatId={}, file={}", chatId, backupFilePath);
            
            // Clean up
            configStorage.clearSession(chatId);
            try {
                cn.hutool.core.io.FileUtil.del(backupFile);
                log.info("Backup file deleted: {}", backupFilePath);
            } catch (Exception e) {
                log.warn("Failed to delete backup file: {}", backupFilePath, e);
            }
            
            // Send success message
            sendMessage(chatId,
                "✅ *数据恢复成功*\n\n" +
                "💡 说明：\n" +
                "数据已成功恢复，系统正在重新初始化。\n\n" +
                "⚠️ 重要提示：\n" +
                "• 建议重启服务以确保所有配置生效\n" +
                "• 恢复后请检查配置是否正常\n" +
                "• 如有问题，请查看系统日志",
                true
            );
            
        } catch (Exception e) {
            log.error("Failed to restore data: chatId={}, file={}", chatId, backupFilePath, e);
            
            // Clean up file on error
            try {
                cn.hutool.core.io.FileUtil.del(backupFile);
            } catch (Exception ex) {
                log.warn("Failed to delete backup file after error: {}", backupFilePath, ex);
            }
            sendMessage(chatId, 
                "❌ *数据恢复失败*\n\n" +
                "错误信息：" + e.getMessage() + "\n\n" +
                "💡 可能原因：\n" +
                "• 密码错误（加密备份）\n" +
                "• 备份文件损坏\n" +
                "• 备份文件不匹配",
                true
            );
            configStorage.clearSession(chatId);
            cn.hutool.core.io.FileUtil.del(backupFilePath);
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

    /**
     * 处理 AI 对话
     */
    private void handleAiChat(long chatId, String message) {
        try {
            // Send typing indicator
            sendMessage(chatId, "🤔 思考中...", false);

            // Call AI service asynchronously
            AiChatService aiChatService = SpringUtil.getBean(AiChatService.class);
            CompletableFuture<String> future = aiChatService.chat(chatId, message);

            // Wait for response and send
            future.thenAccept(response -> {
                // Format response with proper Markdown
                String formattedResponse = MarkdownFormatter.formatAiResponse(response);
                sendMessage(chatId, formattedResponse, true);
            }).exceptionally(ex -> {
                log.error("AI chat failed", ex);
                sendMessage(chatId, "❌ AI 对话失败: " + ex.getMessage(), false);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to handle AI chat", e);
            sendMessage(chatId, "❌ 处理失败: " + e.getMessage(), false);
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
            // Simple parsing logic (Validation)
            String user = getValueFromConfig(text, "user");
            String fingerprint = getValueFromConfig(text, "fingerprint");
            String tenancy = getValueFromConfig(text, "tenancy");
            String region = getValueFromConfig(text, "region");

            if (user == null || fingerprint == null || tenancy == null || region == null) {
                sendMessage(chatId, 
                    "❌ *配置格式错误*\n\n" +
                    "未检测到必要的字段 (user, fingerprint, tenancy, region)。\n" +
                    "请检查复制的内容是否完整。\n\n" +
                    "请重新输入，或发送 /cancel 取消。"
                , true);
                return;
            }
            
            // Store parsed data
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("user", user);
            data.put("fingerprint", fingerprint);
            data.put("tenancy", tenancy);
            data.put("region", region);
            data.put("rawConfig", text); // Keep raw config if needed later
            
            // Transition to next step
            storage.startAddAccountKey(chatId, data);
            
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
        if (!text.contains("BEGIN") && !text.contains("PRIVATE KEY")) {
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
            
            // Download file
            String fileId = document.getFileId();
            org.telegram.telegrambots.meta.api.methods.GetFile getFile = new org.telegram.telegrambots.meta.api.methods.GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File tgFile = telegramClient.execute(getFile);
            java.io.File downloadedFile = telegramClient.downloadFile(tgFile);
            
            // Read content
            String keyContent = cn.hutool.core.io.FileUtil.readUtf8String(downloadedFile);
            
            // Validate content
            if (!keyContent.contains("BEGIN") && !keyContent.contains("PRIVATE KEY")) {
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
            
            // Retrieve data
            java.util.Map<String, Object> data = state.getData();
            String userOctId = (String) data.get("user");
            String fingerprint = (String) data.get("fingerprint");
            String tenancy = (String) data.get("tenancy");
            String region = (String) data.get("region");
            String keyContent = (String) data.get("keyContent");
            
            // Save Key File to persistent volume-mounted directory
            // Key file naming convention: {configured-dir}/oci_api_key_{username}_{timestamp}.pem
            // Use configured keyDirPath instead of ~/.oci to ensure persistence across container updates
            String keyDir = keyDirPath;
            if (!cn.hutool.core.io.FileUtil.exist(keyDir)) {
                cn.hutool.core.io.FileUtil.mkdir(keyDir);
            }
            
            // Clean remark to be filename safe
            String safeRemark = remark.replaceAll("[^a-zA-Z0-9_-]", "_");
            String keyFileName = String.format("oci_api_key_%s_%d.pem", safeRemark, System.currentTimeMillis());
            String keyPath = keyDir + java.io.File.separator + keyFileName;
            
            cn.hutool.core.io.FileUtil.writeUtf8String(keyContent, keyPath);
            // Set permissions (600) for security (Linux only, but good practice)
            try {
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    java.nio.file.Files.setPosixFilePermissions(java.nio.file.Paths.get(keyPath), 
                        java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
                }
            } catch (Exception ignored) {}

            // Save User to DB
            com.tony.kingdetective.service.IOciUserService userService = SpringUtil.getBean(com.tony.kingdetective.service.IOciUserService.class);
            
            com.tony.kingdetective.bean.entity.OciUser ociUser = new com.tony.kingdetective.bean.entity.OciUser();
            ociUser.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr());
            ociUser.setUsername(remark);
            ociUser.setOciTenantId(tenancy);
            ociUser.setOciUserId(userOctId);
            ociUser.setOciFingerprint(fingerprint);
            ociUser.setOciRegion(region);
            ociUser.setOciKeyPath(keyPath);
            ociUser.setTenantName(remark); // Default tenant name to remark
            ociUser.setCreateTime(java.time.LocalDateTime.now());
            ociUser.setDeleted(0);
            
            userService.save(ociUser);

            // fix: 添加账号后立即验证 OCI 连通性，Key 无效时立即提示并回滚
            try {
                sendMessage(chatId, "⏳ 正在验证 OCI API 连通性...");
                com.tony.kingdetective.bean.dto.SysUserDTO sysUserDTO =
                        com.tony.kingdetective.bean.dto.SysUserDTO.builder()
                        .username(remark)
                        .ociCfg(com.tony.kingdetective.bean.dto.SysUserDTO.OciCfg.builder()
                                .tenantId(tenancy)
                                .userId(userOctId)
                                .fingerprint(fingerprint)
                                .region(region)
                                .privateKey(keyContent)
                                .build())
                        .build();
                com.tony.kingdetective.config.OracleInstanceFetcher fetcher =
                        new com.tony.kingdetective.config.OracleInstanceFetcher(sysUserDTO);
                // listAllRegions() 是最轻量的 OCI API 调用，失败则认为 Key 无效
                java.util.List<?> verifiedRegions = fetcher.listAllRegions();
                fetcher.close();
                log.info("OCI connectivity verified for new account: chatId={}, remark={}, regions={}", chatId, remark, verifiedRegions.size());
            } catch (Exception verifyEx) {
                // 验证失败：回滚删除刚保存的记录，提示用户
                log.error("OCI connectivity check failed for chatId={}, remark={}", chatId, remark, verifyEx);
                try { userService.removeById(ociUser.getId()); } catch (Exception ignored) {}
                // 清理已保存的 key 文件
                try { cn.hutool.core.io.FileUtil.del(keyPath); } catch (Exception ignored) {}
                storage.clearSession(chatId);
                sendMessage(chatId,
                    "❌ *账户添加失败 - OCI 连通性验证不通过*\n\n" +
                    "错误信息：" + verifyEx.getMessage() + "\n\n" +
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

            sendMessage(chatId,
                String.format("🎉 *账户添加成功！*\n\n" +
                              "备注名: %s\n" +
                              "区域: %s\n" +
                              "状态: ✅ 已验证 OCI 连通性\n\n" +
                              "您可以点击下方按钮管理该账户。", remark, region),
                true
            );

            // Clear session
            storage.clearSession(chatId);
            
            // Show new account list
            // (We could trigger AccountManagementHandler here, but simple message is safer)

        } catch (Exception e) {
            log.error("Failed to save new account", e);
            sendMessage(chatId, "❌ 保存失败: " + e.getMessage());
            // Don't clear session so user can retry remark or key if needed? No, fail fast.
            storage.clearSession(chatId);
        }
    }

    private String getValueFromConfig(String text, String key) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(key + "=") || line.startsWith(key + " =")) {
                return line.split("=", 2)[1].trim();
            }
        }
        return null;
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
                        "*AI 聊天：*\n" +
                        "├ 直接发送消息即可与 AI 对话\n" +
                        "├ 在主菜单选择 \"AI 聊天\" 进行设置\n\n" +
                        "*SSH 管理：*\n" +
                        "├ `/ssh_config host port user pwd` - 配置连接\n" +
                        "├ `/ssh [命令]` - 执行 SSH 命令\n" +
                        "└ 示例: `/ssh ls -la`\n\n" +
                        "💡 更多功能请点击 /start 查看主菜单";

        // Format and send with Markdown enabled
        String formattedText = MarkdownFormatter.formatMarkdown(helpText);
        sendMessage(chatId, formattedText, true);
    }

    /**
     * 使用处理器工厂处理回调查询（使用虚拟线程避免阻塞）
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        log.info("Handling callback query: callbackData={}, chatId={}", callbackData, chatId);

        // 检查权限
        if (!isAuthorized(chatId)) {
            log.warn("Unauthorized callback attempt: chatId={}", chatId);
            sendUnauthorizedMessage(chatId);
            return;
        }

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
                        telegramClient.execute(response);
                        log.info("Successfully executed callback response: callbackData={}", callbackData);
                    } else {
                        log.warn("Handler returned null response: handler={}, callbackData={}", 
                            handler.getClass().getSimpleName(), callbackData);
                    }
                } else {
                    log.warn("未找到处理回调数据的处理器: callbackData={}", callbackData);
                }
            } catch (TelegramApiException e) {
                log.error("处理回调查询失败: callbackData={}, error={}", callbackData, e.getMessage(), e);
                // Try to notify user about the error
                try {
                    sendMessage(chatId, "❌ 处理请求时发生错误，请重试");
                } catch (Exception ex) {
                    log.error("Failed to send error message to user", ex);
                }
            } catch (Exception e) {
                log.error("处理回调时发生意外错误: callbackData={}, errorType={}, message={}", 
                    callbackData, e.getClass().getSimpleName(), e.getMessage(), e);
                // Try to notify user about the error
                try {
                    sendMessage(chatId, "❌ 系统错误，请重试或联系管理员");
                } catch (Exception ex) {
                    log.error("Failed to send error message to user", ex);
                }
            }
        });
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
                    .text("❌ 无权限操作此机器人🤖，项目地址：https://github.com/tony-wang1990/Wang-Detective")
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
                    .text("请选择需要执行的操作：")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(KeyboardBuilder.buildMainMenu())
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送主菜单失败", e);
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
            // Truncate message if too long
            String truncatedText = MarkdownFormatter.truncate(text);

            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId)
                    .text(truncatedText);

            // Enable Markdown only if requested
            if (enableMarkdown) {
                builder.parseMode("Markdown");
            }

            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("发送消息失败: text={}", text, e);

            // Fallback: try sending without Markdown
            if (enableMarkdown) {
                try {
                    telegramClient.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .build());
                    log.info("消息重新发送成功（不使用 Markdown）");
                } catch (TelegramApiException fallbackEx) {
                    log.error("消息重新发送也失败", fallbackEx);
                }
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
