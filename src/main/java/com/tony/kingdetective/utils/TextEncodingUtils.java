package com.tony.kingdetective.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TextEncodingUtils {

    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final List<String> STRONG_MOJIBAKE_MARKERS = List.of("銆", "€", "�");
    private static final List<String> WEAK_MOJIBAKE_MARKERS = List.of(
            "鐨", "涓", "绋", "鐢", "鍖", "寮", "閰", "鏋", "榛", "浠",
            "搴", "缃", "绯", "熺", "勫", "嬫", "夊", "涔"
    );

    private TextEncodingUtils() {
    }

    public static String repairMojibake(String text) {
        if (text == null || text.isBlank() || !looksLikeMojibake(text)) {
            return text;
        }
        try {
            String repaired = new String(text.getBytes(GB18030), StandardCharsets.UTF_8);
            return mojibakeScore(repaired) < mojibakeScore(text) ? repaired : text;
        } catch (Exception ignored) {
            return text;
        }
    }

    private static boolean looksLikeMojibake(String text) {
        for (String marker : STRONG_MOJIBAKE_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return mojibakeScore(text) >= 3;
    }

    private static int mojibakeScore(String text) {
        int score = 0;
        for (String marker : STRONG_MOJIBAKE_MARKERS) {
            score += count(text, marker) * 4;
        }
        for (String marker : WEAK_MOJIBAKE_MARKERS) {
            score += count(text, marker);
        }
        return score;
    }

    private static int count(String text, String marker) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(marker, index)) >= 0) {
            count++;
            index += marker.length();
        }
        return count;
    }
}
