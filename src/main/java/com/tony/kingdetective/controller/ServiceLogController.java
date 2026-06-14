package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.TextEncodingUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
public class ServiceLogController {

    @GetMapping("/recent")
    public ResponseData<Map<String, Object>> recent(@RequestParam(value = "limit", required = false, defaultValue = "200") int limit,
                                                    @RequestParam(value = "level", required = false) String level,
                                                    @RequestParam(value = "keyword", required = false) String keyword) throws IOException {
        int cappedLimit = Math.max(1, Math.min(limit, 1000));
        Path logPath = Path.of(CommonUtils.LOG_FILE_PATH);
        if (!Files.exists(logPath) || !Files.isRegularFile(logPath)) {
            return ResponseData.successData(Map.of(
                    "path", CommonUtils.LOG_FILE_PATH,
                    "exists", false,
                    "lines", List.of()
            ));
        }

        List<String> lines = tail(logPath, cappedLimit, level, keyword);
        return ResponseData.successData(Map.of(
                "path", CommonUtils.LOG_FILE_PATH,
                "exists", true,
                "lines", lines
        ));
    }

    private List<String> tail(Path logPath, int limit, String level, String keyword) throws IOException {
        String normalizedLevel = normalize(level);
        String normalizedKeyword = normalize(keyword);
        ArrayDeque<String> deque = new ArrayDeque<>(limit);

        try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String displayLine = TextEncodingUtils.repairMojibake(line);
                if (!matches(displayLine, normalizedLevel, normalizedKeyword)) {
                    continue;
                }
                if (deque.size() >= limit) {
                    deque.pollFirst();
                }
                deque.addLast(displayLine);
            }
        }
        return new ArrayList<>(deque);
    }

    private boolean matches(String line, String level, String keyword) {
        String normalizedLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (!level.isBlank() && !normalizedLine.contains(level)) {
            return false;
        }
        return keyword.isBlank() || normalizedLine.contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
