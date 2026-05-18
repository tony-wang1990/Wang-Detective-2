package com.tony.kingdetective.bean.params.ops;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SshHostSaveParams {
    @NotBlank(message = "Host name cannot be blank")
    private String name;

    @NotBlank(message = "SSH host cannot be blank")
    private String host;

    private Integer port = 22;

    @NotBlank(message = "SSH username cannot be blank")
    private String username;

    private String authType = "password";

    private String password;

    private String privateKey;

    private String passphrase;

    private String tags;

    private String hostGroup;

    private String description;
}
