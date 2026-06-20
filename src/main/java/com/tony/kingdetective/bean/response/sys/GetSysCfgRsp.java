package com.tony.kingdetective.bean.response.sys;

import lombok.Data;

/**
 * @projectName: king-detective
 * @package: com.tony.kingdetective.bean.response.sys
 * @className: GetSysCfgRsp
 * @author: Tony Wang
 * @date: 2024/11/30 20:14
 */
@Data
public class GetSysCfgRsp {

    private String dingToken;
    private String dingSecret;
    private String tgChatId;
    private String tgBotToken;
    private Boolean enableMfa;
    private String mfaSecret;
    private String mfaQrData;

    private Boolean enableDailyBroadcast;
    private String dailyBroadcastCron;
    private Boolean enableVersionInform;

    private String bootBroadcastToken;
    private Boolean enableGoogleLogin;
    private String googleClientId;
    private String allowedEmails;
    private Boolean enableKeepAlive;

    private String adminAccount;

}
