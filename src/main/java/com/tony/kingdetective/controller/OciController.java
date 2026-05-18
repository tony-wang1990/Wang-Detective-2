package com.tony.kingdetective.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.dto.InstanceCfgDTO;
import com.tony.kingdetective.bean.params.*;
import com.tony.kingdetective.bean.params.oci.cfg.*;
import com.tony.kingdetective.bean.params.oci.instance.*;
import com.tony.kingdetective.bean.params.oci.securityrule.ReleaseSecurityRuleParams;
import com.tony.kingdetective.bean.params.oci.task.CreateTaskPageParams;
import com.tony.kingdetective.bean.params.oci.task.StopChangeIpParams;
import com.tony.kingdetective.bean.params.oci.task.StopCreateParams;
import com.tony.kingdetective.bean.params.oci.volume.UpdateBootVolumeCfgParams;
import com.tony.kingdetective.bean.response.oci.task.CreateTaskRsp;
import com.tony.kingdetective.bean.response.oci.cfg.OciCfgDetailsRsp;
import com.tony.kingdetective.bean.response.oci.cfg.OciUserListRsp;
import com.tony.kingdetective.service.IInstanceService;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.IOciService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * OciController
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 17:17
 */
@RestController
@RequestMapping(path = "/api/oci")
public class OciController {

    @Resource
    private IOciService ociService;
    @Resource
    private IInstanceService instanceService;
    @Resource
    private IAuditLogService auditLogService;

    @PostMapping(path = "/userPage")
    public ResponseData<Page<OciUserListRsp>> userPage(@Validated @RequestBody GetOciUserListParams params) {
        return ResponseData.successData(ociService.userPage(params), "获取用户分页成功");
    }

    @PostMapping(path = "/addCfg")
    public ResponseData<Void> addCfg(@Validated AddCfgParams params) {
        audited("OCI_CONFIG_ADD", safe(params.getUsername()), "upload key file and config", () -> ociService.addCfg(params));
        return ResponseData.successData("新增配置成功");
    }

    @PostMapping(path = "/updateCfgName")
    public ResponseData<Void> updateCfgName(@Validated @RequestBody UpdateCfgNameParams params) {
        audited("OCI_CONFIG_RENAME", safe(params.getCfgId()), "name=" + safe(params.getUpdateCfgName()), () -> ociService.updateCfgName(params));
        return ResponseData.successData();
    }

    @PostMapping(path = "/uploadCfg")
    public ResponseData<Void> uploadCfg(@Validated UploadCfgParams params) {
        audited("OCI_CONFIG_UPLOAD", "fileList", "count=" + (params.getFileList() == null ? 0 : params.getFileList().size()), () -> ociService.uploadCfg(params));
        return ResponseData.successData("上传配置成功");
    }

    @PostMapping(path = "/removeCfg")
    public ResponseData<Void> removeCfg(@Validated @RequestBody IdListParams params) {
        audited("OCI_CONFIG_DELETE", ids(params.getIdList()), "delete local OCI config records", () -> ociService.removeCfg(params));
        return ResponseData.successData("删除配置成功");
    }

    @PostMapping(path = "/createInstance")
    public ResponseData<Void> createInstance(@Validated @RequestBody CreateInstanceParams params) {
        audited("OCI_TASK_CREATE_INSTANCE", safe(params.getUserId()), "single create instance task", () -> ociService.createInstance(params));
        return ResponseData.successData("创建开机任务成功");
    }

    @PostMapping(path = "/details")
    public ResponseData<OciCfgDetailsRsp> details(@Validated @RequestBody GetOciCfgDetailsParams params) {
        return ResponseData.successData(ociService.details(params), "获取配置详情成功");
    }

    @PostMapping(path = "/changeIp")
    public ResponseData<Void> changeIp(@Validated @RequestBody ChangeIpParams params) {
        audited("OCI_INSTANCE_CHANGE_IP", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()) + ", vnicId=" + safe(params.getVnicId()), () -> ociService.changeIp(params));
        return ResponseData.successData("创建实例更换IP任务成功");
    }

    @PostMapping(path = "/stopCreate")
    public ResponseData<Void> stopCreate(@Validated @RequestBody StopCreateParams params) {
        audited("OCI_TASK_STOP_CREATE", safe(params.getUserId()), "stop create task", () -> ociService.stopCreate(params));
        return ResponseData.successData("停止开机任务成功");
    }

    @PostMapping(path = "/stopChangeIp")
    public ResponseData<Void> stopChangeIp(@Validated @RequestBody StopChangeIpParams params) {
        audited("OCI_TASK_STOP_CHANGE_IP", safe(params.getInstanceId()), "stop change ip task", () -> ociService.stopChangeIp(params));
        return ResponseData.successData("停止更换IP任务成功");
    }

    @PostMapping(path = "/createTaskPage")
    public ResponseData<Page<CreateTaskRsp>> createTaskPage(@Validated @RequestBody CreateTaskPageParams params) {
        return ResponseData.successData(ociService.createTaskPage(params), "获取开机任务列表成功");
    }

    @PostMapping(path = "/stopCreateBatch")
    public ResponseData<Void> stopCreateBatch(@Validated @RequestBody IdListParams params) {
        audited("OCI_TASK_STOP_CREATE_BATCH", ids(params.getIdList()), "batch stop create tasks", () -> ociService.stopCreateBatch(params));
        return ResponseData.successData("停止开机任务成功");
    }

    @PostMapping(path = "/createInstanceBatch")
    public ResponseData<Void> createInstanceBatch(@Validated @RequestBody CreateInstanceBatchParams params) {
        audited("OCI_TASK_CREATE_INSTANCE_BATCH", ids(params.getUserIds()), "batch create instance tasks", () -> ociService.createInstanceBatch(params));
        return ResponseData.successData("批量创建开机任务成功");
    }

    @PostMapping(path = "/updateInstanceState")
    public ResponseData<Void> updateInstanceState(@Validated @RequestBody UpdateInstanceStateParams params) {
        audited("OCI_INSTANCE_STATE", safe(params.getInstanceId()), "action=" + safe(params.getAction()) + ", cfgId=" + safe(params.getOciCfgId()), () -> ociService.updateInstanceState(params));
        return ResponseData.successData("更新实例状态成功");
    }

    @PostMapping(path = "/sendCaptcha")
    public ResponseData<Void> sendCaptcha(@Validated @RequestBody SendCaptchaParams params) {
        audited("OCI_INSTANCE_TERMINATE_CAPTCHA", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()), () -> ociService.sendCaptcha(params));
        return ResponseData.successData("验证码已发送，请查看TG或钉钉消息");
    }

    @PostMapping(path = "/terminateInstance")
    public ResponseData<Void> terminateInstance(@Validated @RequestBody TerminateInstanceParams params) {
        audited("OCI_INSTANCE_TERMINATE", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()) + ", preserveBootVolume=" + params.getPreserveBootVolume(), () -> ociService.terminateInstance(params));
        return ResponseData.successData("终止实例命令已下发");
    }

    @PostMapping(path = "/releaseSecurityRule")
    public ResponseData<Void> releaseSecurityRule(@Validated @RequestBody ReleaseSecurityRuleParams params) {
        audited("OCI_SECURITY_RULE_RELEASE", safe(params.getOciCfgId()), "release security rule", () -> ociService.releaseSecurityRule(params));
        return ResponseData.successData("安全列表放行成功");
    }

    @PostMapping(path = "/getInstanceCfgInfo")
    public ResponseData<InstanceCfgDTO> getInstanceCfgInfo(@Validated @RequestBody GetInstanceCfgInfoParams params) {
        return ResponseData.successData(ociService.getInstanceCfgInfo(params), "获取实例配置信息成功");
    }

    @PostMapping(path = "/createIpv6")
    public ResponseData<Void> createIpv6(@Validated @RequestBody CreateIpv6Params params) {
        audited("OCI_INSTANCE_IPV6_CREATE", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()), () -> ociService.createIpv6(params));
        return ResponseData.successData("为实例附加 IPV6 成功");
    }

    @PostMapping(path = "/updateInstanceName")
    public ResponseData<Void> updateInstanceName(@Validated @RequestBody UpdateInstanceNameParams params) {
        audited("OCI_INSTANCE_RENAME", safe(params.getInstanceId()), "name=" + safe(params.getName()) + ", cfgId=" + safe(params.getOciCfgId()), () -> ociService.updateInstanceName(params));
        return ResponseData.successData("修改实例名称成功");
    }

    @PostMapping(path = "/updateInstanceCfg")
    public ResponseData<Void> updateInstanceCfg(@Validated @RequestBody UpdateInstanceCfgParams params) {
        audited("OCI_INSTANCE_RESIZE", safe(params.getInstanceId()), "ocpus=" + safe(params.getOcpus()) + ", memory=" + safe(params.getMemory()), () -> ociService.updateInstanceCfg(params));
        return ResponseData.successData("修改实例配置成功");
    }

    @PostMapping(path = "/updateBootVolumeCfg")
    public ResponseData<Void> updateBootVolumeCfg(@Validated @RequestBody UpdateBootVolumeCfgParams params) {
        audited("OCI_BOOT_VOLUME_UPDATE", safe(params.getInstanceId()), "size=" + safe(params.getBootVolumeSize()) + ", vpu=" + safe(params.getBootVolumeVpu()), () -> ociService.updateBootVolumeCfg(params));
        return ResponseData.successData("修改引导卷配置成功");
    }

    @PostMapping(path = "/checkAlive")
    public ResponseData<Void> checkAlive() {
        return ResponseData.successData(ociService.checkAlive());
    }

    @PostMapping(path = "/startVnc")
    public ResponseData<String> startVnc(@Validated @RequestBody StartVncParams params) {
        return ResponseData.successData(auditedResult("OCI_INSTANCE_VNC_START", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()), () -> ociService.startVnc(params)));
    }

    @PostMapping(path = "/autoRescue")
    public ResponseData<Void> autoRescue(@Validated @RequestBody AutoRescueParams params) {
        audited("OCI_INSTANCE_AUTO_RESCUE", safe(params.getInstanceId()), "name=" + safe(params.getName()) + ", keepBackupVolume=" + params.getKeepBackupVolume(), () -> ociService.autoRescue(params));
        return ResponseData.successData();
    }

    @PostMapping(path = "/oneClick500M")
    public ResponseData<Void> oneClick500M(@Validated @RequestBody CreateNetworkLoadBalancerParams params) {
        audited("OCI_INSTANCE_500M_ENABLE", safe(params.getInstanceId()), "cfgId=" + safe(params.getOciCfgId()) + ", sshPort=" + params.getSshPort(), () -> instanceService.oneClick500M(params));
        return ResponseData.successData("一键开启下行500Mbps任务下发成功");
    }

    @PostMapping(path = "/oneClickClose500M")
    public ResponseData<Void> oneClickClose500M(@Validated @RequestBody Close500MParams params) {
        audited("OCI_INSTANCE_500M_DISABLE", safe(params.getInstanceId()), "retainBl=" + params.getRetainBl() + ", retainNatGw=" + params.getRetainNatGw(), () -> instanceService.oneClickClose500M(params));
        return ResponseData.successData("关闭下行500Mbps任务下发成功");
    }

    @PostMapping(path = "/updateInstanceShape")
    public ResponseData<Void> updateInstanceShape(@Validated @RequestBody UpdateShapeParams params) {
        audited("OCI_INSTANCE_SHAPE_UPDATE", safe(params.getInstanceId()), "shape=" + safe(params.getShape()) + ", cfgId=" + safe(params.getOciCfgId()), () -> instanceService.updateInstanceShape(params));
        return ResponseData.successData("修改实例 Shape 成功");
    }

    private void audited(String operation, String target, String details, AuditedAction action) {
        try {
            action.run();
            auditLogService.logSuccess(null, operation, safe(target), limit(details));
        } catch (RuntimeException e) {
            auditLogService.logFailure(null, operation, safe(target), limit(e.getMessage()));
            throw e;
        }
    }

    private <T> T auditedResult(String operation, String target, String details, AuditedSupplier<T> supplier) {
        try {
            T result = supplier.get();
            auditLogService.logSuccess(null, operation, safe(target), limit(details));
            return result;
        } catch (RuntimeException e) {
            auditLogService.logFailure(null, operation, safe(target), limit(e.getMessage()));
            throw e;
        }
    }

    private String ids(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "-";
        }
        if (ids.size() <= 5) {
            return String.join(",", ids);
        }
        return String.join(",", ids.subList(0, 5)) + "...+" + (ids.size() - 5);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : limit(value.replace('\n', ' ').replace('\r', ' '));
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }

    @FunctionalInterface
    private interface AuditedAction {
        void run();
    }

    @FunctionalInterface
    private interface AuditedSupplier<T> {
        T get();
    }
}
