package com.tony.kingdetective.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.params.oci.securityrule.AddEgressSecurityRuleParams;
import com.tony.kingdetective.bean.params.oci.securityrule.AddIngressSecurityRuleParams;
import com.tony.kingdetective.bean.params.oci.securityrule.GetSecurityRuleListPageParams;
import com.tony.kingdetective.bean.params.oci.securityrule.RemoveSecurityRuleParams;
import com.tony.kingdetective.bean.response.oci.securityrule.SecurityRuleListRsp;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.ISecurityRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 * SecurityRuleController
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:48
 */
@RestController
@RequestMapping(path = "/api/securityRule")
public class SecurityRuleController {

    @Resource
    private ISecurityRuleService securityRuleService;
    @Resource
    private IAuditLogService auditLogService;

    @RequestMapping("/page")
    public ResponseData<Page<SecurityRuleListRsp.SecurityRuleInfo>> page(@Validated @RequestBody GetSecurityRuleListPageParams params) {
        return ResponseData.successData(securityRuleService.page(params));
    }

    @RequestMapping("/addIngress")
    public ResponseData<Void> addIngress(@Validated @RequestBody AddIngressSecurityRuleParams params){
        audited("OCI_SECURITY_RULE_ADD_INGRESS", params.getOciCfgId(), summarize(params), () -> securityRuleService.addIngress(params));
        return ResponseData.successData();
    }

    @RequestMapping("/addEgress")
    public ResponseData<Void> addEgress(@Validated @RequestBody AddEgressSecurityRuleParams params){
        audited("OCI_SECURITY_RULE_ADD_EGRESS", params.getOciCfgId(), summarize(params), () -> securityRuleService.addEgress(params));
        return ResponseData.successData();
    }

    @RequestMapping("/remove")
    public ResponseData<Void> remove(@Validated @RequestBody RemoveSecurityRuleParams params){
        audited("OCI_SECURITY_RULE_REMOVE", params.getOciCfgId(), summarize(params), () -> securityRuleService.remove(params));
        return ResponseData.successData();
    }

    private void audited(String operation, String target, String details, AuditedAction action) {
        try {
            action.run();
            auditLogService.logSuccess(null, operation, safe(target), safe(details));
        } catch (RuntimeException e) {
            auditLogService.logFailure(null, operation, safe(target), safe(e.getMessage()));
            throw e;
        }
    }

    private String summarize(Object value) {
        return safe(String.valueOf(value));
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }

    @FunctionalInterface
    private interface AuditedAction {
        void run();
    }
}
