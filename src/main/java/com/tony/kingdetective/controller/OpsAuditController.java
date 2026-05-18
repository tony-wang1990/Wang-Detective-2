package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.entity.AuditLog;
import com.tony.kingdetective.service.IAuditLogService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ops/audit")
public class OpsAuditController {
    private final IAuditLogService auditLogService;

    public OpsAuditController(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/recent")
    public ResponseData<List<AuditLog>> recent(@RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        return ResponseData.successData(auditLogService.recent(limit));
    }

    @GetMapping("/search")
    public ResponseData<List<AuditLog>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "success", required = false) Boolean success,
                                               @RequestParam(value = "operation", required = false) String operation,
                                               @RequestParam(value = "limit", required = false, defaultValue = "200") int limit) {
        return ResponseData.successData(auditLogService.search(keyword, success, operation, limit));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "success", required = false) Boolean success,
                                         @RequestParam(value = "operation", required = false) String operation,
                                         @RequestParam(value = "limit", required = false, defaultValue = "1000") int limit) {
        String csv = auditLogService.exportCsv(keyword, success, operation, limit);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("wang-detective-audit.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }
}
