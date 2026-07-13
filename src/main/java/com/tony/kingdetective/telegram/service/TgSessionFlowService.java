package com.tony.kingdetective.telegram.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.bean.params.sys.BackupParams;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.enums.SysCfgTypeEnum;
import com.tony.kingdetective.service.IOciKvService;
import com.tony.kingdetective.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * TG Bot 会话流程 Service（VNC配置流程 + 备份恢复流程）
 *
 * 重构说明（TgBot 瘦身 Round-2）：
 * 原 TgBot 中 handleVncUrlInput / handleBackupPasswordInput / handleRestorePasswordInput
 * 包含大量直接调用 Spring Bean、数据库、文件操作的业务逻辑。
 * 提取到本 Service 后，TgBot 只负责消息路由与发送。
 *
 * @author Tony Wang
 */
@Slf4j
@Service
public class TgSessionFlowService {

    @Resource
    private IOciKvService kvService;

    @Resource
    private ISysService sysService;

    // ==========================================
    // VNC 配置流程
    // ==========================================

    /**
     * 保存 VNC URL 到数据库
     *
     * @param url 已经过格式校验的 URL（调用方需先校验 http/https 前缀）
     * @return null 表示成功；否则返回错误信息
     */
    public String saveVncUrl(String url) {
        try {
            LambdaQueryWrapper<OciKv> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OciKv::getCode, SysCfgEnum.SYS_VNC.getCode());
            OciKv vncConfig = kvService.getOne(wrapper);

            if (vncConfig != null) {
                vncConfig.setValue(url);
                kvService.updateById(vncConfig);
            } else {
                vncConfig = new OciKv();
                vncConfig.setId(IdUtil.getSnowflakeNextIdStr());
                vncConfig.setCode(SysCfgEnum.SYS_VNC.getCode());
                vncConfig.setValue(url);
                vncConfig.setType(SysCfgTypeEnum.SYS_INIT_CFG.getCode());
                kvService.save(vncConfig);
            }
            log.info("VNC URL saved: {}", url);
            return null;
        } catch (Exception e) {
            log.error("Failed to save VNC URL", e);
            return e.getMessage();
        }
    }

    /**
     * 校验 VNC URL 格式
     */
    public boolean isValidVncUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * 标准化 VNC URL（去除尾部斜杠）
     */
    public String normalizeVncUrl(String url) {
        url = url.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ==========================================
    // 备份流程
    // ==========================================

    /**
     * 执行加密备份，返回生成的备份文件路径
     *
     * @param password 备份加密密码（不为空）
     * @return 备份文件路径；失败抛出异常
     */
    public String createEncryptedBackup(String password) throws Exception {
        BackupParams params = new BackupParams();
        params.setEnableEnc(true);
        params.setPassword(password);
        String backupFilePath = sysService.createBackupFile(params);
        log.info("Encrypted backup created: {}", backupFilePath);
        return backupFilePath;
    }

    // ==========================================
    // 恢复流程
    // ==========================================

    /**
     * 执行数据恢复
     *
     * @param backupFilePath 备份文件路径
     * @param password       解密密码（可为空表示无加密）
     */
    public void restoreFromBackup(String backupFilePath, String password) throws Exception {
        try {
            sysService.recoverFromFile(backupFilePath, password);
        } catch (Exception e) {
            // 若密码过短，尝试无密码恢复
            if (password == null || password.isEmpty() || password.length() < 3) {
                log.info("Retrying restore without password for: {}", backupFilePath);
                sysService.recoverFromFile(backupFilePath, "");
            } else {
                throw e;
            }
        }
    }

    /**
     * 删除备份文件（恢复完成后清理）
     */
    public void deleteBackupFile(String path) {
        try {
            FileUtil.del(path);
            log.info("Backup file deleted: {}", path);
        } catch (Exception e) {
            log.warn("Failed to delete backup file: {}", path, e);
        }
    }

    /**
     * 校验备份密码（至少8位）
     */
    public boolean isValidBackupPassword(String password) {
        return password != null && password.trim().length() >= 8;
    }
}
