package com.tony.kingdetective.bean.response.ops;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SshHostRsp {
    private String id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String authType;
    private Boolean hasPassword;
    private Boolean hasPrivateKey;
    private String tags;
    private String hostGroup;
    private String description;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
