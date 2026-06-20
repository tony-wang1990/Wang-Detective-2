package com.tony.kingdetective.telegram.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown Formatter Utility for Telegram Bot
 * <p>
 * 使用 MarkdownV2 模式，提供正确的特殊字符转义。
 * MarkdownV2 要求对以下字符转义：_ * [ ] ( ) ~ ` > # + - = | { } . !
 * 支持 *bold*、_italic_、`code`、```code block```、[link](url)
 *
 * @author Tony Wang / refactored for MarkdownV2 correctness
 */
public class MarkdownFormatter {

    /**
     * MarkdownV2 需要转义的所有特殊字符
     */
    private static final String SPECIAL_CHARS_V2 = "_*[]()~`>#+=|{}.!\\-";

    /**
     * 将普通文本转义为 MarkdownV2 安全字符串（不含任何 Markdown 格式）
     * 用于纯文本内容，确保所有特殊字符被转义，不会被 Telegram 误解析
     *
     * @param text 原始文本
     * @return 转义后的 MarkdownV2 文本
     */
    public static String escapeV2(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (SPECIAL_CHARS_V2.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 格式化含有 Markdown 标记的文本，用于 MarkdownV2 发送。
     * <p>
     * 策略：
     * 1. 识别代码块 ```...``` 内容保持原样（内容不转义）
     * 2. 识别行内代码 `...` 内容保持原样
     * 3. 识别 **bold**、*italic*、__underline__、_italic_
     * 4. 识别 [text](url) 链接
     * 5. 其余普通文本全部转义特殊字符
     *
     * @param text 含 Markdown 标记的原始文本
     * @return MarkdownV2 安全的格式化文本
     */
    public static String formatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return processWithMarkdownV2(text);
    }

    /**
     * 截断超长消息（Telegram 限制 4096 字符）
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n\\.\\.\\.\n\\(消息过长，已截断\\)";
    }

    /**
     * 截断超长消息（使用默认最大长度 4000）
     *
     * @param text 原始文本
     * @return 截断后的文本
     */
    public static String truncate(String text) {
        return truncate(text, 4000);
    }

    // ================================================================
    // 核心解析实现
    // ================================================================

    /**
     * 核心 MarkdownV2 处理器：
     * 逐段解析文本，识别 Markdown 标记区域和普通文本，分别处理。
     */
    private static String processWithMarkdownV2(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            // ---- 代码块 ``` ... ``` ----
            if (i + 2 < len && text.startsWith("```", i)) {
                int closeIdx = text.indexOf("```", i + 3);
                if (closeIdx != -1) {
                    // 提取语言标识和内容
                    String block = text.substring(i + 3, closeIdx);
                    // 提取第一行（可能是语言标识）
                    int newlineIdx = block.indexOf('\n');
                    String lang = "";
                    String code = block;
                    if (newlineIdx != -1) {
                        lang = block.substring(0, newlineIdx);
                        code = block.substring(newlineIdx + 1);
                    }
                    result.append("```");
                    // 语言标识不需要转义，但保留
                    if (!lang.isBlank()) {
                        result.append(lang);
                        result.append("\n");
                    }
                    // 代码内容中只需转义反引号
                    result.append(code.replace("`", "\\`"));
                    result.append("```");
                    i = closeIdx + 3;
                    continue;
                }
                // 没有找到闭合 ```，转义这三个反引号并继续
                result.append("\\`\\`\\`");
                i += 3;
                continue;
            }

            // ---- 行内代码 ` ... ` ----
            if (text.charAt(i) == '`') {
                int closeIdx = text.indexOf('`', i + 1);
                if (closeIdx != -1) {
                    String code = text.substring(i + 1, closeIdx);
                    result.append("`");
                    result.append(code.replace("`", "\\`"));
                    result.append("`");
                    i = closeIdx + 1;
                    continue;
                }
                // 单独的反引号，转义
                result.append("\\`");
                i++;
                continue;
            }

            // ---- 粗体 **...** ----
            if (i + 1 < len && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int closeIdx = text.indexOf("**", i + 2);
                if (closeIdx != -1 && closeIdx > i + 2) {
                    String inner = text.substring(i + 2, closeIdx);
                    result.append("*");
                    result.append(escapeV2(inner));
                    result.append("*");
                    i = closeIdx + 2;
                    continue;
                }
                // 未找到闭合，转义
                result.append("\\*\\*");
                i += 2;
                continue;
            }

            // ---- 斜体/粗体 *...* ----
            if (text.charAt(i) == '*') {
                int closeIdx = text.indexOf('*', i + 1);
                if (closeIdx != -1 && closeIdx > i + 1) {
                    String inner = text.substring(i + 1, closeIdx);
                    result.append("*");
                    result.append(escapeV2(inner));
                    result.append("*");
                    i = closeIdx + 1;
                    continue;
                }
                result.append("\\*");
                i++;
                continue;
            }

            // ---- 下划线/斜体 __...__ ----
            if (i + 1 < len && text.charAt(i) == '_' && text.charAt(i + 1) == '_') {
                int closeIdx = text.indexOf("__", i + 2);
                if (closeIdx != -1 && closeIdx > i + 2) {
                    String inner = text.substring(i + 2, closeIdx);
                    result.append("__");
                    result.append(escapeV2(inner));
                    result.append("__");
                    i = closeIdx + 2;
                    continue;
                }
                result.append("\\_\\_");
                i += 2;
                continue;
            }

            // ---- 斜体 _..._ ----
            if (text.charAt(i) == '_') {
                int closeIdx = text.indexOf('_', i + 1);
                if (closeIdx != -1 && closeIdx > i + 1) {
                    String inner = text.substring(i + 1, closeIdx);
                    result.append("_");
                    result.append(escapeV2(inner));
                    result.append("_");
                    i = closeIdx + 1;
                    continue;
                }
                result.append("\\_");
                i++;
                continue;
            }

            // ---- 链接 [text](url) ----
            if (text.charAt(i) == '[') {
                int closeBracket = text.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket + 1 < len && text.charAt(closeBracket + 1) == '(') {
                    int closeParen = text.indexOf(')', closeBracket + 2);
                    if (closeParen != -1) {
                        String linkText = text.substring(i + 1, closeBracket);
                        String url = text.substring(closeBracket + 2, closeParen);
                        result.append("[");
                        result.append(escapeV2(linkText));
                        result.append("](");
                        // URL 中只需转义 ) 和 \
                        result.append(url.replace("\\", "\\\\").replace(")", "\\)"));
                        result.append(")");
                        i = closeParen + 1;
                        continue;
                    }
                }
                result.append("\\[");
                i++;
                continue;
            }

            // ---- 普通字符：转义 MarkdownV2 特殊字符 ----
            char c = text.charAt(i);
            if (SPECIAL_CHARS_V2.indexOf(c) >= 0) {
                result.append('\\');
            }
            result.append(c);
            i++;
        }

        return result.toString();
    }

    // ================================================================
    // 兼容旧接口
    // ================================================================

    /**
     * @deprecated 请使用 {@link #escapeV2(String)} 或 {@link #formatMarkdown(String)}
     */
    @Deprecated
    public static String formatMarkdownV2(String text) {
        return formatMarkdown(text);
    }

    /**
     * @deprecated 请使用 {@link #formatMarkdown(String)}
     */
    @Deprecated
    public static String formatPlainText(String text) {
        return escapeV2(text);
    }
}
