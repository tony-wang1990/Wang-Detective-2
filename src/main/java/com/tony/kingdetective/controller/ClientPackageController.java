package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientPackageController {

    private static final String ANDROID_FILE = "wang-detective-latest.apk";
    private static final String WINDOWS_FILE = "Wang-Detective-Setup-latest.exe";

    @Value("${clients.download-dir:/app/king-detective/deploy/downloads}")
    private String clientDownloadDir;

    @Value("${clients.version:0.1.0}")
    private String clientVersion;

    @GetMapping("/packages")
    public ResponseData<Map<String, Object>> packages(HttpServletRequest request) {
        String serverBaseUrl = externalBaseUrl(request);
        String version = clientVersion;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverBaseUrl", serverBaseUrl);
        data.put("apiBaseUrl", serverBaseUrl + "/api");
        data.put("downloadBaseUrl", serverBaseUrl + "/downloads");
        data.put("version", version);
        data.put("generatedAt", Instant.now().toString());
        data.put("packages", List.of(
                filePackage(
                        "android",
                        "Android APP",
                        "Android 手机/平板",
                        ANDROID_FILE,
                        serverBaseUrl,
                        version,
                        List.of("完整使用现有移动端控制台功能", "首次登录填写 VPS 地址，与 Web/Windows 共用同一套数据", "安装包放入 deploy/downloads 后自动显示为可下载")
                ),
                filePackage(
                        "windows",
                        "Windows 客户端",
                        "Windows 10/11",
                        WINDOWS_FILE,
                        serverBaseUrl,
                        version,
                        List.of("Electron 独立窗口运行控制台", "首次登录填写 VPS 地址，后续和 Web/Android 共享同一套数据", "未签名安装包在 Windows 中可能需要选择继续安装")
                ),
                webPackage(serverBaseUrl, version)
        ));
        return ResponseData.successData(data, "客户端安装包信息读取成功");
    }

    private Map<String, Object> filePackage(String id,
                                            String name,
                                            String platform,
                                            String fileName,
                                            String serverBaseUrl,
                                            String version,
                                            List<String> notes) {
        Path file = Paths.get(clientDownloadDir, fileName).normalize();
        boolean available = Files.isRegularFile(file);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("platform", platform);
        item.put("version", version);
        item.put("fileName", fileName);
        item.put("downloadUrl", serverBaseUrl + "/downloads/" + fileName);
        item.put("available", available);
        item.put("status", available ? "available" : "missing");
        item.put("notes", notes);
        if (available) {
            try {
                item.put("sizeBytes", Files.size(file));
                item.put("updatedAt", Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString());
                item.put("sha256", sha256(file));
            } catch (Exception e) {
                item.put("status", "metadata-error");
                item.put("error", e.getMessage());
            }
        }
        return item;
    }

    private Map<String, Object> webPackage(String serverBaseUrl, String version) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "web");
        item.put("name", "Web / PWA");
        item.put("platform", "Chrome / Edge");
        item.put("version", version);
        item.put("downloadUrl", serverBaseUrl);
        item.put("available", true);
        item.put("status", "online");
        item.put("notes", List.of("无需下载安装包，浏览器直接访问控制台", "可通过浏览器菜单安装为桌面应用", "与 Android 和 Windows 共用同一套 VPS 数据"));
        return item;
    }

    private String sha256(Path file) throws Exception {
        Path sidecar = Paths.get(file.toString() + ".sha256");
        if (Files.isRegularFile(sidecar)) {
            String text = Files.readString(sidecar).trim();
            if (!text.isEmpty()) {
                String candidate = text.split("\\s+")[0];
                if (candidate.matches("(?i)[0-9a-f]{64}")) {
                    return candidate.toLowerCase();
                }
            }
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String externalBaseUrl(HttpServletRequest request) {
        String proto = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }

        String host = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            boolean defaultPort = ("https".equalsIgnoreCase(proto) && port == 443)
                    || ("http".equalsIgnoreCase(proto) && port == 80);
            host = request.getServerName() + (defaultPort ? "" : ":" + port);
        }

        String prefix = firstForwardedValue(request.getHeader("X-Forwarded-Prefix"));
        if (prefix == null || prefix.isBlank()) {
            prefix = request.getContextPath();
        }
        if (prefix == null || "/".equals(prefix)) {
            prefix = "";
        }
        if (!prefix.isEmpty() && !prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        return proto + "://" + host + prefix.replaceAll("/+$", "");
    }

    private String firstForwardedValue(String value) {
        if (value == null) {
            return null;
        }
        return value.split(",")[0].trim();
    }
}
