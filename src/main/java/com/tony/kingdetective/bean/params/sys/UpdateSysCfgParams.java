package com.tony.kingdetective.bean.params.sys;

import lombok.Data;

/**
 * @projectName: king-detective
 * @package: com.tony.kingdetective.bean.params.sys
 * @className: UpdateLoginCfgParams
 * @author: Tony Wang
 * @date: 2024/11/30 18:22
 */
@Data
public class UpdateSysCfgParams {

    private String dingToken;
    private String dingSecret;
    private String tgChatId;
    private String tgBotToken;
    private Boolean enableMfa;

    private Boolean enableDailyBroadcast;
    private String dailyBroadcastCron;
    private Boolean enableVersionInform;

    private String bootBroadcastToken;
    private Boolean enableGoogleLogin;
    private String googleClientId;
    private String allowedEmails;
    private Boolean enableKeepAlive;
}
