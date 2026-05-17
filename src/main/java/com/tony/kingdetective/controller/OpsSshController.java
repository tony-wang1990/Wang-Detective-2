package com.tony.kingdetective.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.params.ops.SftpParams;
import com.tony.kingdetective.bean.params.ops.SshBatchCommandParams;
import com.tony.kingdetective.bean.params.ops.SshCommandParams;
import com.tony.kingdetective.bean.params.ops.SshCredentialParams;
import com.tony.kingdetective.bean.params.ops.SshHostSaveParams;
import com.tony.kingdetective.bean.params.ops.SshSessionCreateParams;
import com.tony.kingdetective.bean.response.ops.SftpListRsp;
import com.tony.kingdetective.bean.response.ops.SshCommandRsp;
import com.tony.kingdetective.bean.response.ops.SshHostRsp;
import com.tony.kingdetective.bean.response.ops.SshSessionRsp;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.ops.SshHostService;
import com.tony.kingdetective.service.ops.WebSshService;
import com.tony.kingdetective.service.ops.WebSshSessionRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ops")
public class OpsSshController {
    private final WebSshService webSshService;
    private final WebSshSessionRegistry sessionRegistry;
    private final SshHostService sshHostService;
    private final IAuditLogService auditLogService;

    public OpsSshController(WebSshService webSshService,
                            WebSshSessionRegistry sessionRegistry,
                            SshHostService sshHostService,
                            IAuditLogService auditLogService) {
        this.webSshService = webSshService;
        this.sessionRegistry = sessionRegistry;
        this.sshHostService = sshHostService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/ssh/hosts")
    public ResponseData<List<SshHostRsp>> hosts(@RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseData.successData(sshHostService.list(keyword));
    }

    @GetMapping("/ssh/hosts/{id}")
    public ResponseData<SshHostRsp> host(@PathVariable("id") String id) {
        return ResponseData.successData(sshHostService.get(id));
    }

    @PostMapping("/ssh/hosts")
    public ResponseData<SshHostRsp> createHost(@Valid @RequestBody SshHostSaveParams params) {
        SshHostRsp host = sshHostService.create(params);
        auditSuccess("OPS_SSH_HOST_CREATE", host.getId(), host.getName() + "@" + host.getHost());
        return ResponseData.successData(host);
    }

    @PutMapping("/ssh/hosts/{id}")
    public ResponseData<SshHostRsp> updateHost(@PathVariable("id") String id, @Valid @RequestBody SshHostSaveParams params) {
        SshHostRsp host = sshHostService.update(id, params);
        auditSuccess("OPS_SSH_HOST_UPDATE", id, host.getName() + "@" + host.getHost());
        return ResponseData.successData(host);
    }

    @DeleteMapping("/ssh/hosts/{id}")
    public ResponseData<Void> deleteHost(@PathVariable("id") String id) {
        sshHostService.delete(id);
        auditSuccess("OPS_SSH_HOST_DELETE", id, null);
        return ResponseData.successData("Host deleted");
    }

    @PostMapping("/ssh/hosts/{id}/test")
    public ResponseData<Boolean> testHost(@PathVariable("id") String id) {
        return ResponseData.successData(webSshService.testConnection(sshHostService.credentialForHost(id)));
    }

    @PostMapping("/ssh/session")
    public ResponseData<SshSessionRsp> createSession(@Valid @RequestBody SshSessionCreateParams params) {
        if (params.getCredential() == null) {
            throw new OciException(-1, "SSH credential is required");
        }
        SshCredentialParams credential = sshHostService.resolveCredential(params.getCredential());
        int ttlMinutes = params.getTtlMinutes() == null ? 15 : params.getTtlMinutes();
        WebSshSessionRegistry.Entry entry = sessionRegistry.create(credential, ttlMinutes);
        auditSuccess("OPS_SSH_SESSION_CREATE", target(credential), "ttlMinutes=" + ttlMinutes);
        return ResponseData.successData(toSessionRsp(entry));
    }

    @GetMapping("/ssh/sessions")
    public ResponseData<List<SshSessionRsp>> sessions() {
        return ResponseData.successData(sessionRegistry.list().stream()
                .map(this::toSessionRsp)
                .toList());
    }

    @DeleteMapping("/ssh/sessions/{sessionId}")
    public ResponseData<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        WebSshSessionRegistry.Entry entry = sessionRegistry.get(sessionId);
        sessionRegistry.remove(sessionId);
        auditSuccess("OPS_SSH_SESSION_DELETE", sessionId, entry == null ? null : target(entry.credential()));
        return ResponseData.successData("Session deleted");
    }

    @PostMapping("/ssh/test")
    public ResponseData<Boolean> test(@RequestBody SshSessionCreateParams params) {
        if (params.getCredential() == null) {
            throw new OciException(-1, "SSH credential is required");
        }
        return ResponseData.successData(webSshService.testConnection(sshHostService.resolveCredential(params.getCredential())));
    }

    @PostMapping("/ssh/exec")
    public ResponseData<SshCommandRsp> execute(@Valid @RequestBody SshCommandParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        SshCommandRsp rsp = webSshService.execute(params);
        auditSuccess("OPS_SSH_EXEC", target(params.getCredential()), detail("command", params.getCommand()));
        return ResponseData.successData(rsp);
    }

    @PostMapping("/ssh/batch")
    public ResponseData<List<SshCommandRsp>> batch(@Valid @RequestBody SshBatchCommandParams params) {
        if (CollectionUtil.isEmpty(params.getTargets())) {
            throw new OciException(-1, "At least one SSH target is required");
        }
        List<SshCommandRsp> results = params.getTargets().parallelStream()
                .map(target -> {
                    SshCommandParams commandParams = new SshCommandParams();
                    commandParams.setCredential(sshHostService.resolveCredential(target.getCredential()));
                    commandParams.setCommand(params.getCommand());
                    commandParams.setTimeoutSeconds(params.getTimeoutSeconds());
                    SshCommandRsp rsp = webSshService.execute(commandParams);
                    rsp.setName(target.getName());
                    return rsp;
                })
                .toList();
        auditSuccess("OPS_SSH_BATCH", "targets=" + params.getTargets().size(), detail("command", params.getCommand()));
        return ResponseData.successData(results);
    }

    @PostMapping("/sftp/list")
    public ResponseData<SftpListRsp> list(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        return ResponseData.successData(webSshService.list(params));
    }

    @PostMapping("/sftp/read")
    public ResponseData<String> read(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        return ResponseData.successData(webSshService.readText(params));
    }

    @PostMapping("/sftp/download")
    public ResponseEntity<byte[]> download(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        byte[] bytes = webSshService.download(params);
        auditSuccess("OPS_SFTP_DOWNLOAD", target(params.getCredential()), params.getPath());
        String filename = filename(params.getPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping(value = "/sftp/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseData<Void> upload(@RequestParam("hostId") String hostId,
                                     @RequestParam("path") String path,
                                     @RequestParam("file") MultipartFile file) throws Exception {
        SftpParams params = new SftpParams();
        SshCredentialParams credential = new SshCredentialParams();
        credential.setHostId(hostId);
        params.setCredential(sshHostService.resolveCredential(credential));
        params.setPath(uploadPath(path, file.getOriginalFilename()));
        try (InputStream input = file.getInputStream()) {
            webSshService.upload(params, input);
        }
        auditSuccess("OPS_SFTP_UPLOAD", hostId, params.getPath() + " size=" + file.getSize());
        return ResponseData.successData("File uploaded");
    }

    @PostMapping("/sftp/write")
    public ResponseData<Void> write(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        webSshService.writeText(params);
        auditSuccess("OPS_SFTP_WRITE", target(params.getCredential()), params.getPath());
        return ResponseData.successData("File saved");
    }

    @PostMapping("/sftp/mkdir")
    public ResponseData<Void> mkdir(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        webSshService.mkdir(params);
        auditSuccess("OPS_SFTP_MKDIR", target(params.getCredential()), params.getPath());
        return ResponseData.successData("Directory created");
    }

    @PostMapping("/sftp/delete")
    public ResponseData<Void> delete(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        webSshService.delete(params);
        auditSuccess("OPS_SFTP_DELETE", target(params.getCredential()), params.getPath());
        return ResponseData.successData("Path deleted");
    }

    @PostMapping("/sftp/rename")
    public ResponseData<Void> rename(@RequestBody SftpParams params) {
        params.setCredential(sshHostService.resolveCredential(params.getCredential()));
        webSshService.rename(params);
        auditSuccess("OPS_SFTP_RENAME", target(params.getCredential()), params.getPath() + " -> " + params.getTargetPath());
        return ResponseData.successData("Path renamed");
    }

    private void auditSuccess(String operation, String target, String details) {
        auditLogService.logSuccess(null, operation, target, trim(details));
    }

    private SshSessionRsp toSessionRsp(WebSshSessionRegistry.Entry entry) {
        SshCredentialParams credential = entry.credential();
        return SshSessionRsp.builder()
                .sessionId(entry.sessionId())
                .host(credential.getHost())
                .port(credential.getPort())
                .username(credential.getUsername())
                .createdAt(entry.createdAt())
                .lastConnectedAt(entry.lastConnectedAt())
                .connectCount(entry.connectCount())
                .expiresAt(entry.expiresAt())
                .websocketPath("/ops/ssh/terminal/" + entry.sessionId())
                .build();
    }

    private String target(SshCredentialParams credential) {
        if (credential == null) {
            return "unknown";
        }
        if (credential.getHostId() != null && !credential.getHostId().isBlank()) {
            return credential.getHostId();
        }
        return credential.getUsername() + "@" + credential.getHost() + ":" + credential.getPort();
    }

    private String detail(String key, String value) {
        return key + "=" + trim(value);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 240 ? value : value.substring(0, 240) + "...";
    }

    private String uploadPath(String path, String filename) {
        String safeFilename = filename(filename);
        String value = path == null || path.isBlank() ? safeFilename : path.trim();
        if (value.endsWith("/") || value.endsWith("\\")) {
            return value + safeFilename;
        }
        return value;
    }

    private String filename(String path) {
        if (path == null || path.isBlank()) {
            return "download.bin";
        }
        String normalized = path.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        String name = index >= 0 ? normalized.substring(index + 1) : normalized;
        return name.isBlank() ? "download.bin" : name;
    }
}
