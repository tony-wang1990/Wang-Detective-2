package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.params.sys.*;
import com.tony.kingdetective.bean.response.sys.GetGlanceRsp;
import com.tony.kingdetective.bean.response.sys.GetSysCfgRsp;
import com.tony.kingdetective.bean.response.sys.LoginRsp;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.service.IIpBlacklistService;
import com.tony.kingdetective.service.ILoginAttemptService;
import com.tony.kingdetective.service.ISysService;
import com.tony.kingdetective.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @projectName: king-detective
 * @package: com.tony.kingdetective.controller
 * @className: SysCfgController
 * @author: Tony Wang
 * @date: 2024/11/30 17:07
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/sys")
public class SysCfgController {

    @Resource
    private ISysService sysService;
    
    @Resource
    private ILoginAttemptService loginAttemptService;
    
    @Resource
    private IIpBlacklistService blacklistService;
    
    @Resource
    private HttpServletRequest request;

    @PostMapping(path = "/login")
    public ResponseData<LoginRsp> addCfg(@Validated @RequestBody LoginParams params) {
        String clientIp = getClientIp(request);
        
        try {
            // Clean expired login attempts
            loginAttemptService.cleanExpiredAttempts();
            
            LoginRsp result = sysService.login(params);
            
            // Login success - clear attempts
            loginAttemptService.clearAttempts(clientIp);
            
            return ResponseData.successData(result, "登录成功");
        } catch (OciException e) {
            // Login failed - record attempt
            loginAttemptService.recordFailure(clientIp);
            int attemptCount = loginAttemptService.getAttemptCount(clientIp);
            
            // Auto-ban after 5 failures (no notification)
            if (attemptCount >= 5) {
                blacklistService.addToBlacklist(clientIp, "Login failed 5 times", "AUTO");
                log.warn("IP {} automatically blacklisted after 5 failed login attempts", clientIp);
                // Do NOT send notification
            }
            
            throw e;
        }
    }
    
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @PostMapping(path = "/updateVersion")
    public ResponseData<Void> updateVersion() {
        sysService.updateVersion();
        return ResponseData.successData("版本更新任务下发成功，请稍后刷新网页查看~");
    }

    @PostMapping(path = "/getEnableMfa")
    public ResponseData<Boolean> getEnableMfa() {
        return ResponseData.successData(sysService.getEnableMfa(), "获取系统是否启用MFA成功");
    }

    @PostMapping(path = "/getSysCfg")
    public ResponseData<GetSysCfgRsp> getSysCfg() {
        return ResponseData.successData(sysService.getSysCfg(), "获取系统配置成功");
    }

    @PostMapping(path = "/updateSysCfg")
    public ResponseData<Void> updateSysCfg(@Validated @RequestBody UpdateSysCfgParams params) {
        sysService.updateSysCfg(params);
        return ResponseData.successData("更新系统配置成功");
    }

    @PostMapping(path = "/updateAdminCredential")
    public ResponseData<Void> updateAdminCredential(@Validated @RequestBody UpdateAdminCredentialParams params) {
        sysService.updateAdminCredential(params);
        return ResponseData.successData("登录账号密码已更新，请重新登录");
    }

    @PostMapping(path = "/sendMsg")
    public ResponseData<Void> sendMsg(@Validated @RequestBody SendMsgParams params) {
        sysService.sendMessage(params.getMessage());
        return ResponseData.successData("发送消息成功");
    }

    @PostMapping(path = "/checkMfaCode")
    public ResponseData<Void> checkMfaCode(@Validated @RequestBody CheckMfaCodeParams params) {
        sysService.checkMfaCode(params.getMfaCode());
        return ResponseData.successData("MFA验证通过");
    }

    @PostMapping(path = "/backup")
    public void backup(@Validated @RequestBody BackupParams params) {
        sysService.backup(params);
    }

    @PostMapping(path = "/recover")
    public ResponseData<Void> recover(@Validated @ModelAttribute RecoverParams params) {
        sysService.recover(params);
        return ResponseData.successData("恢复数据成功");
    }

    @GetMapping(path = "/glance")
    public ResponseData<GetGlanceRsp> glance() {
        return ResponseData.successData(sysService.glance(), "获取仪表盘数据成功");
    }

    @PostMapping(path = "/googleLogin")
    public ResponseData<LoginRsp> googleLogin(@Validated @RequestBody GoogleLoginParams params) {
        return ResponseData.successData(sysService.googleLogin(params), "Google登录成功");
    }

    @PostMapping(path = "/getGoogleClientId")
    public ResponseData<String> getGoogleClientId() {
        return ResponseData.successData(sysService.getGoogleClientId(), "获取Google Client ID成功");
    }
}
