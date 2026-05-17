package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.VersionUpdateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @projectName: king-detective
 * @package: com.tony.kingdetective.controller
 * @className: SystemController
 * @author: Tony Wang
 * @date: 2026/01/04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 触发一键更新
     * 通过修改update_version_trigger.flag文件触发watcher容器更新应用
     */
    @PostMapping("/trigger-update")
    public ResponseData<String> triggerUpdate() {
        try {
            VersionUpdateUtils.triggerUpdate();
            log.info("触发自动更新，timestamp: {}", System.currentTimeMillis());
            return ResponseData.successData("更新触发成功，系统将在几分钟内自动更新并重启");
        } catch (Exception e) {
            log.error("触发更新失败", e);
            return ResponseData.errorData("更新触发失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前版本信息
     */
    @PostMapping("/version")
    public ResponseData<String> getVersion() {
        return ResponseData.successData(CommonUtils.getCurrentVersion());
    }

    @GetMapping("/version-info")
    public ResponseData<Map<String, Object>> getVersionInfo() {
        String currentVersion = CommonUtils.getCurrentVersion();
        String latestVersion = CommonUtils.getLatestVersion();
        Map<String, Object> versionInfo = new LinkedHashMap<>();
        versionInfo.put("currentVersion", currentVersion);
        versionInfo.put("latestVersion", latestVersion);
        versionInfo.put("updateAvailable", VersionUpdateUtils.hasNewVersion(currentVersion, latestVersion));
        versionInfo.put("triggerFile", VersionUpdateUtils.TRIGGER_FILE_PATH);
        versionInfo.put("checkedAt", System.currentTimeMillis());
        return ResponseData.successData(versionInfo);
    }
}
