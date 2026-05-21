package com.tony.kingdetective.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.bean.params.sys.BackupParams;
import com.tony.kingdetective.service.ISysService;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.handler.AbstractCallbackHandler;
import com.tony.kingdetective.telegram.storage.ConfigSessionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backup and Restore Handler
 * Handles system data backup and restore operations
 *
 * @author yohann
 */
@Slf4j
@Component
public class BackupRestoreHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
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

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("💾 创建备份", "backup_create")
        ));

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("📥 恢复数据", "restore_data")
        ));

        keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
        keyboard.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "backup_restore";
    }
}

/**
 * Backup Create Handler
 * Initiates backup creation process
 */
@Component
class BackupCreateHandler extends AbstractCallbackHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BackupCreateHandler.class);

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "💾 *创建备份*\n\n" +
                "请选择备份方式：\n\n" +
                "🔓 **普通备份**\n" +
                "• 不加密，直接导出\n" +
                "• 文件较小，速度快\n" +
                "• 适合测试环境\n\n" +
                "🔒 **加密备份（推荐）**\n" +
                "• 使用密码加密\n" +
                "• 保护敏感信息\n" +
                "• 适合生产环境\n\n" +
                "⚠️ 提示：\n" +
                "加密备份需要设置密码，恢复时需要相同密码。";

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔓 普通备份", "backup_execute_plain"),
                KeyboardBuilder.button("🔒 加密备份", "backup_execute_encrypted")
        ));

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "backup_restore")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "backup_create";
    }
}

/**
 * Backup Execute Plain Handler
 * Executes plain (unencrypted) backup and sends file via TG
 */
@Component
class BackupExecutePlainHandler extends AbstractCallbackHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BackupExecutePlainHandler.class);

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Send processing message
            telegramClient.execute(buildEditMessage(
                    callbackQuery,
                    "⏳ 正在创建备份...\n\n请稍候，这可能需要几秒钟。",
                    null
            ));

            // Execute backup using the new method
            ISysService sysService = SpringUtil.getBean(ISysService.class);
            BackupParams params = new BackupParams();
            params.setEnableEnc(false);
            params.setPassword(""); // 传空字符串而不是null

            String backupFilePath = sysService.createBackupFile(params);

            log.info("Plain backup created for chatId: {}, file: {}", chatId, backupFilePath);

            // Send backup file via Telegram
            java.io.File backupFile = new java.io.File(backupFilePath);
            if (backupFile.exists()) {
                org.telegram.telegrambots.meta.api.methods.send.SendDocument sendDocument = 
                    org.telegram.telegrambots.meta.api.methods.send.SendDocument.builder()
                        .chatId(chatId)
                        .document(new org.telegram.telegrambots.meta.api.objects.InputFile(backupFile))
                        .caption(
                            com.tony.kingdetective.telegram.utils.MarkdownFormatter.formatMarkdown(
                                "📦 *备份文件*\n\n" +
                                "✅ 备份类型：普通备份（未加密）\n" +
                                "📅 创建时间：" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n" +
                                "💡 说明：\n" +
                                "• 此备份文件未加密\n" +
                                "• 请妥善保管，避免泄露\n" +
                                "• 可用于系统数据恢复\n\n" +
                                "⚠️ 重要：\n" +
                                "文件已发送到聊天窗口，\n" +
                                "服务器副本将在发送后删除。"
                            )
                        )
                        .parseMode("MarkdownV2")
                        .build();
                
                try {
                    telegramClient.execute(sendDocument);
                    log.info("Backup file sent to chatId: {}", chatId);
                    
                    // Delete backup file from server after sending
                    cn.hutool.core.io.FileUtil.del(backupFile);
                    log.info("Backup file deleted from server: {}", backupFilePath);
                    
                } catch (Exception e) {
                    log.error("Failed to send backup file", e);
                    throw new Exception("发送备份文件失败：" + e.getMessage());
                }
            } else {
                throw new Exception("备份文件不存在：" + backupFilePath);
            }

            String text = "✅ *备份创建成功*\n\n" +
                    "备份文件已发送到聊天窗口。\n\n" +
                    "💡 提示：\n" +
                    "• 请保存备份文件到安全位置\n" +
                    "• 服务器不会保留备份副本\n" +
                    "• 需要时可随时创建新备份";

            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("◀️ 返回", "backup_restore")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());

            // Send success message
            return buildEditMessage(
                    callbackQuery,
                    text,
                    new InlineKeyboardMarkup(keyboard)
            );

        } catch (Exception e) {
            log.error("Failed to create plain backup", e);

            String text = "❌ *备份创建失败*\n\n" +
                    "错误信息：" + e.getMessage() + "\n\n" +
                    "请检查系统日志或稍后重试。";

            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("◀️ 返回", "backup_restore")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());

            return buildEditMessage(
                    callbackQuery,
                    text,
                    new InlineKeyboardMarkup(keyboard)
            );
        }
    }

    @Override
    public String getCallbackPattern() {
        return "backup_execute_plain";
    }
}

/**
 * Backup Execute Encrypted Handler
 * Prompts for password and executes encrypted backup
 */
@Component
class BackupExecuteEncryptedHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();

        // Mark session as waiting for backup password
        ConfigSessionStorage.getInstance().startBackupPassword(chatId);

        String text = "🔒 *加密备份*\n\n" +
                "请直接发送备份密码：\n\n" +
                "📝 密码要求：\n" +
                "• 建议使用强密码\n" +
                "• 至少 8 位字符\n" +
                "• 包含字母和数字\n\n" +
                "⚠️ 重要提示：\n" +
                "• 请牢记此密码\n" +
                "• 恢复备份时需要相同密码\n" +
                "• 密码丢失将无法恢复数据\n\n" +
                "💡 提示：\n" +
                "发送 /cancel 可取消操作";

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "backup_restore")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "backup_execute_encrypted";
    }
}

/**
 * Restore Data Handler
 * Provides instructions for data restoration via TG upload
 */
@Component
class RestoreDataHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "📥 *恢复数据*\n\n" +
                "⚠️ **重要警告**\n" +
                "恢复操作会覆盖当前所有数据！\n" +
                "请确保已备份当前数据。\n\n" +
                "📝 恢复步骤：\n" +
                "1️⃣ 点击下方「开始恢复」按钮\n" +
                "2️⃣ 上传备份 ZIP 文件\n" +
                "3️⃣ 如果是加密备份，输入密码\n" +
                "4️⃣ 系统自动执行恢复\n\n" +
                "💡 提示：\n" +
                "• 仅支持本系统生成的备份文件\n" +
                "• 文件必须为 ZIP 格式\n" +
                "• 恢复完成后需要重启服务\n" +
                "• 加密备份需要正确的密码\n\n" +
                "⚠️ 注意：\n" +
                "恢复后所有当前数据将被替换，\n" +
                "请谨慎操作！";

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🚀 开始恢复", "restore_start")
        ));

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "backup_restore")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "restore_data";
    }
}

/**
 * Restore Start Handler
 * Prompts user to upload backup file
 */
@Component
class RestoreStartHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();

        // 启动恢复会话
        ConfigSessionStorage.getInstance().startRestorePassword(chatId,
                String.valueOf(callbackQuery.getMessage().getMessageId()));

        String text = "📤 *开始恢复*\n\n" +
                "请上传备份 ZIP 文件：\n\n" +
                "📎 注意事项：\n" +
                "• 只支持 ZIP 格式的备份文件\n" +
                "• 文件必须是本系统生成的备份\n" +
                "• 上传后系统会自动检测是否加密\n\n" +
                "💡 提示：\n" +
                "发送 /cancel 可取消操作";

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "restore_data")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "restore_start";
    }
}
