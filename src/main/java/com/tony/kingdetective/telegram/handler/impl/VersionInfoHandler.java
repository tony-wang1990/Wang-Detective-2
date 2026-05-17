package com.tony.kingdetective.telegram.handler.impl;

import com.tony.kingdetective.utils.VersionUpdateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.Serializable;

import static java.lang.Math.toIntExact;

/**
 * 版本信息回调处理器
 *
 * @author yohann
 */
@Slf4j
@Component
public class VersionInfoHandler extends VersionInfoBaseHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            return getVersionInfo(
                    callbackQuery.getMessage().getChatId(),
                    callbackQuery.getMessage().getMessageId(),
                    telegramClient
            );
        } catch (Exception e) {
            log.error("Handle version info error", e);
            return buildEditMessage(callbackQuery, "获取版本信息失败");
        }
    }

    @Override
    public String getCallbackPattern() {
        return "version_info";
    }
}

/**
 * 更新系统版本回调处理器
 *
 * @author yohann
 */
@Slf4j
@Component
class UpdateSysVersionHandler extends VersionInfoBaseHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        long messageId = callbackQuery.getMessage().getMessageId();

        try {
            VersionUpdateUtils.triggerUpdate();
            log.info("Created update trigger file: {}", VersionUpdateUtils.TRIGGER_FILE_PATH);

            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(toIntExact(messageId))
                    .build());

            return SendMessage.builder()
                    .chatId(chatId)
                    .text("🔄 已触发 W-探长自动更新。\n\n" +
                            "更新过程通常需要 1-3 分钟，完成后容器会自动重启。\n" +
                            "可通过以下命令查看 watcher 进度：\n" +
                            "<code>docker logs -f king-detective-watcher</code>")
                    .parseMode("HTML")
                    .build();
        } catch (IOException e) {
            log.error("Create update trigger file failed", e);
            try {
                telegramClient.execute(DeleteMessage.builder()
                        .chatId(chatId)
                        .messageId(toIntExact(messageId))
                        .build());
            } catch (TelegramApiException ex) {
                log.error("Delete version update message failed", ex);
            }
            return SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ 触发更新失败: " + e.getMessage() + "\n\n请检查容器权限或稍后在 Web 控制台重试。")
                    .build();
        } catch (TelegramApiException e) {
            log.error("TG Bot error", e);
            return null;
        }
    }

    @Override
    public String getCallbackPattern() {
        return "update_sys_version";
    }
}
