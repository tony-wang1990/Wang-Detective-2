package com.tony.kingdetective.telegram.builder;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Telegram Bot é”®ç›˜æž„å»ºå™¨
 *
 * @author yohann
 */
public class KeyboardBuilder {

    /**
     * æž„å»ºä¸»èœå•é”®ç›˜ï¼ˆæ¯è¡Œ4ä¸ªæŒ‰é’®å¸ƒå±€ï¼‰
     *
     * @return é”®ç›˜è¡Œåˆ—è¡¨
     */
    public static List<InlineKeyboardRow> buildMainMenu() {
        return Arrays.asList(
                // å¿«æ·åŠŸèƒ½ï¼ˆé¡¶éƒ¨ï¼‰
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("ðŸš€ ä¸€é”®æŠ¢æœº")
                                .callbackData("config_list")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ðŸ§­ è¿ç»´ä¸­å¿ƒ")
                                .callbackData("ops_center")
                                .build()
                ),

                // ========== ðŸ’¼å®žä¾‹ + ðŸŒç½‘ç»œ ==========
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("å¿«æ·å¼€æœº")
                                .callbackData("quick_start")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ä¸€é”®æµ‹æ´»")
                                .callbackData("check_alive")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("å®žä¾‹å‡é™çº§")
                                .callbackData("shape_change_select")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("è´¦æˆ·ç®¡ç†")
                                .callbackData("account_management")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("è‡ªåŠ¨æ¢IP")
                                .callbackData("auto_ip_change_select")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("å¼€æ”¾ç«¯å£")
                                .callbackData("open_all_ports_select")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("SSHç®¡ç†")
                                .callbackData("ssh_management")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("IPv6ç®¡ç†")
                                .callbackData("ipv6_config_select")
                                .build()
                ),

                // ========== ðŸ“Šèµ„æºç›‘æŽ§ ==========
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("é…é¢æŸ¥è¯¢")
                                .callbackData("quota_query")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("æ¶ˆè´¹æŸ¥è¯¢")
                                .callbackData("cost_query")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("èµ„æºå ç”¨")
                                .callbackData("instance_resource_usage_select")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("å†…å­˜å ç”¨")
                                .callbackData("memory_occupy_select")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("æµé‡åŽ†å²")
                                .callbackData("traffic_history")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("æµé‡ç»Ÿè®¡")
                                .callbackData("traffic_statistics")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("Profileç®¡ç†")
                                .callbackData("profile_management")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("åŒºåŸŸæ‹“å±•")
                                .callbackData("auto_region_expansion")
                                .build()
                ),

                // ========== ðŸ¤–è‡ªåŠ¨åŒ– + ðŸ”å®‰å…¨ ==========
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("ç›‘æŽ§é€šçŸ¥")
                                .callbackData("instance_monitoring")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ç›‘æŽ§è‡ªå¯")
                                .callbackData("auto_restart_monitoring")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("æ¯æ—¥æ—¥æŠ¥")
                                .callbackData("daily_report")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ä»»åŠ¡ç®¡ç†")
                                .callbackData("task_management")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("å®‰å…¨ç®¡ç†")
                                .callbackData("security_management")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("MFAç®¡ç†")
                                .callbackData("mfa_management")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("æ¸…é™¤2FA")
                                .callbackData("clear_2fa_devices")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ç¦ç”¨è¢«å°æˆ·")
                                .callbackData("disable_banned_accounts")
                                .build()
                ),

                // ========== ðŸ› ï¸ç³»ç»Ÿå·¥å…· ==========
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("æ‰¹é‡æŸ¥é‚®")
                                .callbackData("batch_email_query")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("è®¢é˜…ä¿¡æ¯")
                                .callbackData("subscription_info")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("ç‰ˆæœ¬ä¿¡æ¯")
                                .callbackData("version_info")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("æ—¥å¿—æŸ¥è¯¢")
                                .callbackData("log_query")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("VNCé… ç½®")
                                .callbackData("vnc_config")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("å¤‡ä»½æ ¢å¤ ")
                                .callbackData("backup_restore")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🆘 救援中心")
                                .callbackData("rescue_center")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("AIè Šå¤©")
                                .callbackData("ai_chat")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("é€šçŸ¥é¢‘é “")
                                .url("https://t.me/Woci_detective")
                                .build()
                ),

                // ========== 🔗å¤–éƒ¨é“¾æŽ¥ ==========
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("æ”¾è´§æŸ¥è¯¢")
                                .url("https://check.oci-helper.de5.net")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("å¼€æºåœ°å€ï¼ˆå¸®å¿™ç‚¹ç‚¹starâ­ï¼‰")
                                .url("https://github.com/tony-wang1990/Wang-Detective")
                                .build()
                ),

                // å…³é—­æŒ‰é’®
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("âŒ å…³é—­çª—å£")
                                .callbackData("cancel")
                                .build()
                )
        );
    }

    /**
     * æž„å»ºè´¦æˆ·é€‰æ‹©é”®ç›˜
     *
     * @param accounts è´¦æˆ·åˆ—è¡¨
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup buildAccountSelectionKeyboard(List<String> accounts) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        for (String accountId : accounts) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text(accountId)
                    .callbackData("account_detail:" + accountId)
                    .build());
            keyboard.add(row);
        }

        // æ·»åŠ è¿”å›žæŒ‰é’®
        InlineKeyboardRow backRow = new InlineKeyboardRow();
        backRow.add(InlineKeyboardButton.builder()
                .text("Â« è¿”å›žä¸»èœå•")
                .callbackData("back_to_main")
                .build());
        keyboard.add(backRow);

        return new InlineKeyboardMarkup(keyboard);
    }

    /**
     * æž„å»ºç¡®è®¤é”®ç›˜
     *
     * @param confirmCallback ç¡®è®¤å›žè°ƒæ•°æ®
     * @param cancelCallback  å–æ¶ˆå›žè°ƒæ•°æ®
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup buildConfirmationKeyboard(String confirmCallback, String cancelCallback) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text("âœ… ç¡®è®¤")
                .callbackData(confirmCallback)
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("âŒ å–æ¶ˆ")
                .callbackData(cancelCallback)
                .build());

        keyboard.add(row);
        return new InlineKeyboardMarkup(keyboard);
    }

    /**
     * æž„å»ºå¸¦è¿”å›žæŒ‰é’®çš„é”®ç›˜
     *
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup buildBackKeyboard() {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text("Â« è¿”å›žä¸»èœå•")
                .callbackData("back_to_main")
                .build());

        keyboard.add(row);
        return new InlineKeyboardMarkup(keyboard);
    }

    /**
     * æž„å»ºç©ºé”®ç›˜ï¼ˆç”¨äºŽç§»é™¤é”®ç›˜ï¼‰
     *
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup buildEmptyKeyboard() {
        return new InlineKeyboardMarkup(new ArrayList<>());
    }

    /**
     * ä»Žè¡Œåˆ—è¡¨æž„å»ºé”®ç›˜æ ‡è®°
     *
     * @param rows é”®ç›˜è¡Œåˆ—è¡¨
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup fromRows(List<InlineKeyboardRow> rows) {
        return new InlineKeyboardMarkup(rows);
    }

    /**
     * æž„å»ºå–æ¶ˆè¡Œï¼ˆè¿”å›žä¸»èœå•ï¼‰
     *
     * @return InlineKeyboardRow
     */
    public static InlineKeyboardRow buildCancelRow() {
        return new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("âŒ å…³é—­çª—å£")
                        .callbackData("cancel")
                        .build()
        );
    }

    /**
     * æž„å»ºè¿”å›žä¸»èœå•è¡Œ
     *
     * @return InlineKeyboardRow
     */
    public static InlineKeyboardRow buildBackToMainMenuRow() {
        return new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("Â« è¿”å›žä¸»èœå•")
                        .callbackData("cancel")
                        .build()
        );
    }

    /**
     * æž„å»ºåˆ†é¡µè¡Œ
     *
     * @param currentPage å½“å‰é¡µ
     * @param totalPages æ€»é¡µæ•°
     * @param prevCallback ä¸Šä¸€é¡µå›žè°ƒ
     * @param nextCallback ä¸‹ä¸€é¡µå›žè°ƒ
     * @return InlineKeyboardRow
     */
    public static InlineKeyboardRow buildPaginationRow(int currentPage, int totalPages, String prevCallback, String nextCallback) {
        InlineKeyboardRow row = new InlineKeyboardRow();

        if (currentPage > 1) {
            row.add(InlineKeyboardButton.builder()
                    .text("â—€ï¸ ä¸Šä¸€é¡µ")
                    .callbackData(prevCallback)
                    .build());
        }

        row.add(InlineKeyboardButton.builder()
                .text(currentPage + "/" + totalPages)
                .callbackData("page_info")
                .build());

        if (currentPage < totalPages) {
            row.add(InlineKeyboardButton.builder()
                    .text("ä¸‹ä¸€é¡µ â–¶ï¸")
                    .callbackData(nextCallback)
                    .build());
        }

        return row;
    }

    /**
     * å¿«æ·æ–¹æ³•ï¼šåˆ›å»ºæŒ‰é’®
     *
     * @param text æŒ‰é’®æ–‡æœ¬
     * @param callbackData å›žè°ƒæ•°æ®
     * @return InlineKeyboardButton
     */
    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    /**
     * å¿«æ·æ–¹æ³•ï¼šåˆ›å»ºURLæŒ‰é’®
     *
     * @param text æŒ‰é’®æ–‡æœ¬
     * @param url URLåœ°å€
     * @return InlineKeyboardButton
     */
    public static InlineKeyboardButton urlButton(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }
}
