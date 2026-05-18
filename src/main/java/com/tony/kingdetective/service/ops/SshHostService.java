package com.tony.kingdetective.service.ops;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.SshHost;
import com.tony.kingdetective.bean.params.ops.SshCredentialParams;
import com.tony.kingdetective.bean.params.ops.SshHostSaveParams;
import com.tony.kingdetective.bean.response.ops.SshHostRsp;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.mapper.SshHostMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SshHostService {
    private static final String AUTH_PASSWORD = "password";
    private static final String AUTH_PRIVATE_KEY = "privateKey";

    private final SshHostMapper sshHostMapper;
    private final SecretCryptoService secretCryptoService;

    public SshHostService(SshHostMapper sshHostMapper, SecretCryptoService secretCryptoService) {
        this.sshHostMapper = sshHostMapper;
        this.secretCryptoService = secretCryptoService;
    }

    public List<SshHostRsp> list(String keyword) {
        return list(keyword, null);
    }

    public List<SshHostRsp> list(String keyword, String hostGroup) {
        LambdaQueryWrapper<SshHost> wrapper = new LambdaQueryWrapper<SshHost>()
                .orderByDesc(SshHost::getUpdateTime)
                .orderByDesc(SshHost::getCreateTime);
        if (StrUtil.isNotBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(SshHost::getName, value)
                    .or()
                    .like(SshHost::getHost, value)
                    .or()
                    .like(SshHost::getUsername, value)
                    .or()
                    .like(SshHost::getTags, value)
                    .or()
                    .like(SshHost::getHostGroup, value));
        }
        if (StrUtil.isNotBlank(hostGroup)) {
            wrapper.eq(SshHost::getHostGroup, hostGroup.trim());
        }
        return sshHostMapper.selectList(wrapper).stream().map(this::toRsp).toList();
    }

    public SshHostRsp create(SshHostSaveParams params) {
        validateBasic(params);
        SshHost host = new SshHost();
        host.setId(IdUtil.fastSimpleUUID());
        host.setCreateTime(LocalDateTime.now());
        apply(host, params, true);
        sshHostMapper.insert(host);
        return toRsp(host);
    }

    public SshHostRsp update(String id, SshHostSaveParams params) {
        SshHost host = getRequired(id);
        validateBasic(params);
        apply(host, params, false);
        sshHostMapper.updateById(host);
        return toRsp(host);
    }

    public void delete(String id) {
        sshHostMapper.deleteById(id);
    }

    public SshCredentialParams resolveCredential(SshCredentialParams credential) {
        if (credential == null) {
            throw new OciException(-1, "SSH credential is required");
        }
        if (StrUtil.isBlank(credential.getHostId())) {
            return credential;
        }
        return credentialForHost(credential.getHostId());
    }

    public SshCredentialParams credentialForHost(String id) {
        SshHost host = getRequired(id);
        SshCredentialParams credential = new SshCredentialParams();
        credential.setHostId(host.getId());
        credential.setHost(host.getHost());
        credential.setPort(host.getPort());
        credential.setUsername(host.getUsername());
        credential.setPassword(secretCryptoService.decrypt(host.getPasswordCipher()));
        credential.setPrivateKey(secretCryptoService.decrypt(host.getPrivateKeyCipher()));
        credential.setPassphrase(secretCryptoService.decrypt(host.getPassphraseCipher()));
        credential.setConnectTimeoutSeconds(10);
        markUsed(host);
        return credential;
    }

    public SshHostRsp get(String id) {
        return toRsp(getRequired(id));
    }

    private void apply(SshHost host, SshHostSaveParams params, boolean create) {
        String authType = normalizeAuthType(params.getAuthType());
        host.setName(params.getName().trim());
        host.setHost(params.getHost().trim());
        host.setPort(params.getPort() == null || params.getPort() <= 0 ? 22 : params.getPort());
        host.setUsername(params.getUsername().trim());
        host.setAuthType(authType);
        host.setTags(StrUtil.emptyToNull(StrUtil.trim(params.getTags())));
        host.setHostGroup(StrUtil.blankToDefault(StrUtil.trim(params.getHostGroup()), "默认分组"));
        host.setDescription(StrUtil.emptyToNull(StrUtil.trim(params.getDescription())));
        host.setUpdateTime(LocalDateTime.now());

        if (AUTH_PASSWORD.equals(authType)) {
            if (StrUtil.isNotBlank(params.getPassword())) {
                host.setPasswordCipher(secretCryptoService.encrypt(params.getPassword()));
            } else if (create || StrUtil.isBlank(host.getPasswordCipher())) {
                throw new OciException(-1, "SSH password is required");
            }
            host.setPrivateKeyCipher(null);
            host.setPassphraseCipher(null);
        } else {
            if (StrUtil.isNotBlank(params.getPrivateKey())) {
                host.setPrivateKeyCipher(secretCryptoService.encrypt(params.getPrivateKey()));
            } else if (create || StrUtil.isBlank(host.getPrivateKeyCipher())) {
                throw new OciException(-1, "SSH private key is required");
            }
            if (create || StrUtil.isNotBlank(params.getPrivateKey()) || StrUtil.isNotBlank(params.getPassphrase())) {
                host.setPassphraseCipher(secretCryptoService.encrypt(params.getPassphrase()));
            }
            host.setPasswordCipher(null);
        }
    }

    private void validateBasic(SshHostSaveParams params) {
        if (params == null || StrUtil.isBlank(params.getName()) || StrUtil.isBlank(params.getHost()) || StrUtil.isBlank(params.getUsername())) {
            throw new OciException(-1, "Host name, address and username are required");
        }
    }

    private String normalizeAuthType(String authType) {
        if (StrUtil.isBlank(authType) || AUTH_PASSWORD.equalsIgnoreCase(authType)) {
            return AUTH_PASSWORD;
        }
        if (AUTH_PRIVATE_KEY.equalsIgnoreCase(authType) || "private_key".equalsIgnoreCase(authType)) {
            return AUTH_PRIVATE_KEY;
        }
        throw new OciException(-1, "Unsupported SSH auth type: " + authType);
    }

    private SshHost getRequired(String id) {
        if (StrUtil.isBlank(id)) {
            throw new OciException(-1, "SSH host id is required");
        }
        SshHost host = sshHostMapper.selectById(id);
        if (host == null) {
            throw new OciException(-1, "SSH host not found");
        }
        return host;
    }

    private void markUsed(SshHost host) {
        SshHost update = new SshHost();
        update.setId(host.getId());
        update.setLastUsedAt(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        sshHostMapper.updateById(update);
    }

    private SshHostRsp toRsp(SshHost host) {
        return SshHostRsp.builder()
                .id(host.getId())
                .name(host.getName())
                .host(host.getHost())
                .port(host.getPort())
                .username(host.getUsername())
                .authType(host.getAuthType())
                .hasPassword(StrUtil.isNotBlank(host.getPasswordCipher()))
                .hasPrivateKey(StrUtil.isNotBlank(host.getPrivateKeyCipher()))
                .tags(host.getTags())
                .hostGroup(StrUtil.blankToDefault(host.getHostGroup(), "默认分组"))
                .description(host.getDescription())
                .lastUsedAt(host.getLastUsedAt())
                .createTime(host.getCreateTime())
                .updateTime(host.getUpdateTime())
                .build();
    }
}
