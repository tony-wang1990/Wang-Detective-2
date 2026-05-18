package com.tony.kingdetective.bean.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_ssh_host")
public class SshHost {
    @TableId
    private String id;

    private String name;

    private String host;

    private Integer port;

    private String username;

    @TableField("auth_type")
    private String authType;

    @TableField("password_cipher")
    private String passwordCipher;

    @TableField("private_key_cipher")
    private String privateKeyCipher;

    @TableField("passphrase_cipher")
    private String passphraseCipher;

    private String tags;

    @TableField("host_group")
    private String hostGroup;

    private String description;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
