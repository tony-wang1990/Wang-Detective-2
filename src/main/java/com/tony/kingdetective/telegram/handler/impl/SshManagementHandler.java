package com.tony.kingdetective.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.handler.AbstractCallbackHandler;
import com.tony.kingdetective.telegram.service.SshService;
import com.tony.kingdetective.telegram.storage.SshConnectionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SSH Management Handler
 * Handles SSH connection menu and operations
 * 
 * @author yohann
 */
@Slf4j
@Component
public class SshManagementHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        SshConnectionStorage storage = SshConnectionStorage.getInstance();
        
        boolean hasConnection = storage.hasConnection(chatId);
        
        String text;
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        if (hasConnection) {
            SshConnectionStorage.SshInfo info = storage.getConnection(chatId);
            text = String.format(
                "🔌 *SSH 连接管理*\n\n" +
                "📌 当前连接：\n" +
                "• 主机: %s:%d\n" +
                "• 用户: %s\n" +
                "• 状态: ✅ 已配置\n\n" +
                "💡 使用说明：\n" +
                "发送 /ssh [命令] 来执行 SSH 命令\n" +
                "例如: /ssh ls -la\n\n" +
                "⚙️ 请选择功能：",
                info.getHost(),
                info.getPort(),
                info.getUsername()
            );
            
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔄 重新配置", "ssh_setup")
            ));
            
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🧪 测试连接", "ssh_test")
            ));
            
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🗑️ 删除连接", "ssh_disconnect")
            ));
        } else {
            text = "🔌 *SSH 连接管理*\n\n" +
                   "📝 当前没有配置 SSH 连接\n\n" +
                   "💡 使用说明：\n" +
                   "点击下方按钮配置 SSH 连接信息\n" +
                   "配置格式：host port username password\n" +
                   "例如: 192.168.1.100 22 root mypassword\n\n" +
                   "⚙️ 请选择功能：";
            
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("➕ 配置连接", "ssh_setup")
            ));
        }
        
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
        return "ssh_management";
    }
}

/**
 * SSH Setup Handler
 */
@Slf4j
@Component
class SshSetupHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        
        String text = "🔧 *配置 SSH 连接*\n\n" +
                     "请按以下格式发送连接信息：\n\n" +
                     "/ssh_config host port username password\n\n" +
                     "📝 示例：\n" +
                     "/ssh_config 192.168.1.100 22 root mypassword\n\n" +
                     "⚠️ 注意：\n" +
                     "• 参数之间用空格分隔\n" +
                     "• 端口号默认为 22\n" +
                     "• 密码会被安全存储，不会被记录";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "ssh_management")
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
        return "ssh_setup";
    }
}

/**
 * SSH Test Connection Handler
 */
@Slf4j
@Component
class SshTestHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        SshConnectionStorage storage = SshConnectionStorage.getInstance();
        
        if (!storage.hasConnection(chatId)) {
            return buildEditMessage(
                callbackQuery,
                "❌ 未配置 SSH 连接\n\n请先配置连接信息",
                new InlineKeyboardMarkup(List.of(
                    new InlineKeyboardRow(KeyboardBuilder.button("◀️ 返回", "ssh_management"))
                ))
            );
        }
        
        // Send testing message first
        try {
            telegramClient.execute(buildEditMessage(
                callbackQuery,
                "🔄 正在测试连接...",
                null
            ));
        } catch (TelegramApiException e) {
            log.error("Failed to send testing message", e);
        }
        
        SshConnectionStorage.SshInfo info = storage.getConnection(chatId);
        SshService sshService = SpringUtil.getBean(SshService.class);
        
        boolean success = sshService.testConnection(
            info.getHost(),
            info.getPort(),
            info.getUsername(),
            info.getPassword()
        );
        
        String text;
        if (success) {
            text = String.format(
                "✅ *连接测试成功*\n\n" +
                "主机: %s:%d\n" +
                "用户: %s\n\n" +
                "SSH 连接正常，可以执行命令了！",
                info.getHost(),
                info.getPort(),
                info.getUsername()
            );
        } else {
            text = String.format(
                "❌ *连接测试失败*\n\n" +
                "主机: %s:%d\n" +
                "用户: %s\n\n" +
                "请检查：\n" +
                "• 主机地址和端口是否正确\n" +
                "• 用户名和密码是否正确\n" +
                "• 网络连接是否正常\n" +
                "• SSH 服务是否开启",
                info.getHost(),
                info.getPort(),
                info.getUsername()
            );
        }
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "ssh_management")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        // Send result message
        try {
            telegramClient.execute(SendMessage.builder()
                .chatId(chatId)
                .text(com.tony.kingdetective.telegram.utils.MarkdownFormatter.formatMarkdown(text))
                .parseMode("MarkdownV2")
                .replyMarkup(new InlineKeyboardMarkup(keyboard))
                .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send test result", e);
        }
        
        return null;
    }
    
    @Override
    public String getCallbackPattern() {
        return "ssh_test";
    }
}

/**
 * SSH Disconnect Handler
 */
@Slf4j
@Component
class SshDisconnectHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        SshConnectionStorage storage = SshConnectionStorage.getInstance();
        
        storage.removeConnection(chatId);
        log.info("SSH connection removed: chatId={}", chatId);
        
        String text = "✅ SSH 连接信息已删除\n\n需要使用时可以重新配置";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "ssh_management")
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
        return "ssh_disconnect";
    }
}
