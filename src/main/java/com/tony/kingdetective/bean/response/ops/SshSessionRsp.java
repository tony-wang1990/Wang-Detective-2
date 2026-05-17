package com.tony.kingdetective.bean.response.ops;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SshSessionRsp {
    private String sessionId;
    private String host;
    private Integer port;
    private String username;
    private Long createdAt;
    private Long lastConnectedAt;
    private Integer connectCount;
    private Long expiresAt;
    private String websocketPath;
}
