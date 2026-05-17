package com.tony.kingdetective.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public final class VersionUpdateUtils {
    public static final String TRIGGER_FILE_PATH = "/app/king-detective/runtime/update_version_trigger.flag";

    private VersionUpdateUtils() {
    }

    public static void triggerUpdate() throws IOException {
        File triggerFile = new File(TRIGGER_FILE_PATH);
        if (triggerFile.exists() && triggerFile.isDirectory()) {
            FileUtils.deleteDirectory(triggerFile);
        }

        File parentDir = triggerFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create update runtime directory: " + parentDir.getAbsolutePath());
        }

        Files.write(
                triggerFile.toPath(),
                "trigger\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public static boolean hasNewVersion(String currentVersion, String latestVersion) {
        return latestVersion != null
                && !latestVersion.isBlank()
                && currentVersion != null
                && !currentVersion.isBlank()
                && !latestVersion.equals(currentVersion);
    }
}
