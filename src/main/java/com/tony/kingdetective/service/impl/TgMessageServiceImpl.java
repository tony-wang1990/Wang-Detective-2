package com.tony.kingdetective.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.service.IMessageService;
import com.tony.kingdetective.service.IOciKvService;
import com.tony.kingdetective.utils.TextEncodingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <p>
 * TgMessageServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/8 12:06
 */
@Service
@Slf4j
public class TgMessageServiceImpl implements IMessageService {

    @Resource
    private IOciKvService kvService;

    @Value("${telegram.bot.token:${TELEGRAM_BOT_TOKEN:${BOT_TOKEN:}}}")
    private String telegramBotToken;

    @Value("${telegram.bot.chat-id:${TELEGRAM_BOT_CHAT_ID:${TELEGRAM_CHAT_ID:${TG_CHAT_ID:}}}}")
    private String telegramChatId;

    private static final String TG_URL = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
    private static final String TG_SEND_URL = "https://api.telegram.org/bot%s/sendMessage";

    @Override
    public void sendMessage(String message) {
        String botToken = firstNonBlank(cfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN), telegramBotToken);
        String chatId = firstNonBlank(cfgValue(SysCfgEnum.SYS_TG_CHAT_ID), telegramChatId);

        if (StrUtil.isNotBlank(botToken) && StrUtil.isNotBlank(chatId)) {
            doSend(message, botToken, chatId);
        }
    }

    public void sendVersionUpdateMessage(String message) {
        String botToken = firstNonBlank(cfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN), telegramBotToken);
        String chatId = firstNonBlank(cfgValue(SysCfgEnum.SYS_TG_CHAT_ID), telegramChatId);

        if (StrUtil.isNotBlank(botToken) && StrUtil.isNotBlank(chatId)) {
            doSendWithUpdateButton(message, botToken, chatId);
        }
    }

    private String cfgValue(SysCfgEnum cfg) {
        OciKv kv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, cfg.getCode())
                .orderByDesc(OciKv::getCreateTime)
                .last("limit 1"), false);
        return kvValue(kv);
    }

    private String kvValue(OciKv kv) {
        return kv == null ? null : kv.getValue();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void doSend(String message, String botToken, String chatId) {
        try {
            String safeMessage = TextEncodingUtils.repairMojibake(message);
            String encodedMessage = URLEncoder.encode(safeMessage, StandardCharsets.UTF_8.toString());
            String urlString = String.format(TG_URL, botToken, chatId, encodedMessage);
            HttpResponse response = HttpUtil.createGet(urlString).execute();

            if (response.getStatus() == 200) {
                log.info("telegram message send successfully!");
            } else {
                log.info("failed to send telegram message, response code: [{}]", response.getStatus());
            }
        } catch (Exception e) {
            log.error("error while sending telegram message: ", e);
//            throw new RuntimeException(e);
        }
    }

    private void doSendWithUpdateButton(String message, String botToken, String chatId) {
        try {
            String safeMessage = TextEncodingUtils.repairMojibake(message);
            Object updateButton = JSONUtil.createObj()
                    .set("text", "🔄 点击更新")
                    .set("callback_data", "update_sys_version");
            Object versionButton = JSONUtil.createObj()
                    .set("text", "📦 查看版本")
                    .set("callback_data", "version_info");
            String body = JSONUtil.createObj()
                    .set("chat_id", chatId)
                    .set("text", safeMessage)
                    .set("reply_markup", JSONUtil.createObj()
                            .set("inline_keyboard", List.of(List.of(updateButton), List.of(versionButton))))
                    .toString();
            HttpResponse response = HttpUtil.createPost(String.format(TG_SEND_URL, botToken))
                    .contentType("application/json; charset=UTF-8")
                    .body(body)
                    .execute();

            if (response.getStatus() == 200) {
                log.info("telegram version update message send successfully!");
            } else {
                log.info("failed to send telegram version update message, response code: [{}]", response.getStatus());
            }
        } catch (Exception e) {
            log.error("error while sending telegram version update message: ", e);
        }
    }
}
