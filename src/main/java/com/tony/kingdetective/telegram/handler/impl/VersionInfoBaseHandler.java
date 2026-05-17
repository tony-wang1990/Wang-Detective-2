package com.tony.kingdetective.telegram.handler.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.enums.SysCfgTypeEnum;
import com.tony.kingdetective.service.IOciKvService;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.handler.AbstractCallbackHandler;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.VersionUpdateUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.toIntExact;

/**
 * 版本信息基础处理器（共享逻辑）
 *
 * @author yohann
 */
public abstract class VersionInfoBaseHandler extends AbstractCallbackHandler {

    protected BotApiMethod<? extends Serializable> getVersionInfo(long chatId, long messageId, TelegramClient telegramClient) {
        IOciKvService kvService = SpringUtil.getBean(IOciKvService.class);
        String latest = StrUtil.blankToDefault(CommonUtils.getLatestVersion(), CommonUtils.getCurrentVersion());
        String now = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        now = StrUtil.blankToDefault(now, CommonUtils.getCurrentVersion());

        boolean hasNewVersion = VersionUpdateUtils.hasNewVersion(now, latest);
        StringBuilder content = new StringBuilder()
                .append("【版本信息】\n\n")
                .append("当前版本：").append(now).append('\n')
                .append("最新版本：").append(latest).append('\n')
                .append("状态：").append(hasNewVersion ? "发现新版本，可点击下方按钮更新" : "当前已是最新版本").append("\n\n");

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        if (hasNewVersion) {
            String releaseNotes = StrUtil.blankToDefault(CommonUtils.getLatestVersionBody(), "暂无更新说明");
            content.append("更新内容：\n").append(releaseNotes);
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔄 点击更新到最新版本", "update_sys_version")
            ));
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔍 重新检测版本", "version_info")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
        } else {
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔍 重新检测版本", "version_info")
            ));
            keyboard.addAll(KeyboardBuilder.buildMainMenu());
        }

        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(toIntExact(messageId))
                .text(content.toString())
                .replyMarkup(new InlineKeyboardMarkup(keyboard))
                .build();
    }
}
