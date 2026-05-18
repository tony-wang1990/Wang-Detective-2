package com.tony.kingdetective.service.oci;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.oracle.bmc.core.model.BootVolume;
import com.oracle.bmc.core.model.IngressSecurityRule;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.PortRange;
import com.oracle.bmc.core.model.SecurityList;
import com.oracle.bmc.core.model.Vcn;
import com.tony.kingdetective.bean.dto.SysUserDTO;
import com.tony.kingdetective.bean.response.oci.risk.OciRiskReportRsp;
import com.tony.kingdetective.config.OracleInstanceFetcher;
import com.tony.kingdetective.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class OciRiskService {

    private static final List<Integer> HIGH_RISK_PORTS = Arrays.asList(22, 3389, 3306, 5432, 6379, 9200, 5601, 27017, 9527);

    private final ISysService sysService;

    public OciRiskService(ISysService sysService) {
        this.sysService = sysService;
    }

    public OciRiskReportRsp report(Integer maxConfigs) {
        int scanLimit = maxConfigs == null ? 8 : Math.max(1, Math.min(maxConfigs, 50));
        List<SysUserDTO> allConfigs = sysService.list();
        List<OciRiskReportRsp.ConfigRisk> configs = new ArrayList<>();
        List<OciRiskReportRsp.RiskItem> risks = new ArrayList<>();

        SummaryAccumulator summary = new SummaryAccumulator();
        summary.configCount = allConfigs.size();

        for (SysUserDTO config : allConfigs.stream().limit(scanLimit).toList()) {
            summary.scannedConfigCount++;
            OciRiskReportRsp.ConfigRisk configRisk = scanOneConfig(config, risks, summary);
            configs.add(configRisk);
        }

        summary.highRiskCount = (int) risks.stream().filter(risk -> "HIGH".equals(risk.getLevel())).count();
        summary.warnRiskCount = (int) risks.stream().filter(risk -> "WARN".equals(risk.getLevel())).count();

        return OciRiskReportRsp.builder()
                .generatedAt(java.time.LocalDateTime.now())
                .summary(OciRiskReportRsp.Summary.builder()
                        .configCount(summary.configCount)
                        .scannedConfigCount(summary.scannedConfigCount)
                        .instanceCount(summary.instanceCount)
                        .runningInstanceCount(summary.runningInstanceCount)
                        .stoppedInstanceCount(summary.stoppedInstanceCount)
                        .armInstanceCount(summary.armInstanceCount)
                        .armOcpus(round(summary.armOcpus))
                        .armMemoryGb(round(summary.armMemoryGb))
                        .bootVolumeGb(summary.bootVolumeGb)
                        .highRiskCount(summary.highRiskCount)
                        .warnRiskCount(summary.warnRiskCount)
                        .errorConfigCount(summary.errorConfigCount)
                        .build())
                .configs(configs)
                .risks(risks)
                .build();
    }

    private OciRiskReportRsp.ConfigRisk scanOneConfig(SysUserDTO config,
                                                      List<OciRiskReportRsp.RiskItem> risks,
                                                      SummaryAccumulator summary) {
        String configId = config.getOciCfg() == null ? null : config.getOciCfg().getId();
        String configName = StrUtil.blankToDefault(config.getUsername(), configId);
        String region = config.getOciCfg() == null ? null : config.getOciCfg().getRegion();

        int instanceCount = 0;
        int runningCount = 0;
        double armOcpus = 0;
        double armMemoryGb = 0;
        long bootVolumeGb = 0;
        int publicIngressRuleCount = 0;
        int highRiskPortRuleCount = 0;

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(config)) {
            List<Instance> instances = fetcher.listInstances();
            instanceCount = instances.size();
            summary.instanceCount += instanceCount;

            for (Instance instance : instances) {
                if (instance.getLifecycleState() == Instance.LifecycleState.Running) {
                    runningCount++;
                    summary.runningInstanceCount++;
                } else if (instance.getLifecycleState() == Instance.LifecycleState.Stopped) {
                    summary.stoppedInstanceCount++;
                }

                String shape = instance.getShape() == null ? "" : instance.getShape().toLowerCase();
                if (shape.contains("a1.flex")) {
                    summary.armInstanceCount++;
                    double ocpus = instance.getShapeConfig() == null ? 0 : toDouble(instance.getShapeConfig().getOcpus());
                    double memory = instance.getShapeConfig() == null ? 0 : toDouble(instance.getShapeConfig().getMemoryInGBs());
                    armOcpus += ocpus;
                    armMemoryGb += memory;
                    summary.armOcpus += ocpus;
                    summary.armMemoryGb += memory;
                }
            }

            List<BootVolume> bootVolumes = safeListBootVolumes(fetcher);
            for (BootVolume bootVolume : bootVolumes) {
                Long size = bootVolume.getSizeInGBs();
                if (size != null) {
                    bootVolumeGb += size;
                    summary.bootVolumeGb += size;
                }
            }

            List<Vcn> vcns = fetcher.listVcn();
            for (Vcn vcn : vcns) {
                SecurityList securityList = fetcher.listSecurityRule(vcn);
                if (securityList == null || CollectionUtil.isEmpty(securityList.getIngressSecurityRules())) {
                    continue;
                }
                for (IngressSecurityRule rule : securityList.getIngressSecurityRules()) {
                    if (!isPublic(rule)) {
                        continue;
                    }
                    publicIngressRuleCount++;
                    if (isHighRiskPublicPort(rule)) {
                        highRiskPortRuleCount++;
                    }
                }
            }

            addThresholdRisks(risks, configId, configName, region, armOcpus, armMemoryGb, bootVolumeGb,
                    publicIngressRuleCount, highRiskPortRuleCount);

            return OciRiskReportRsp.ConfigRisk.builder()
                    .configId(configId)
                    .configName(configName)
                    .region(region)
                    .instanceCount(instanceCount)
                    .runningInstanceCount(runningCount)
                    .armOcpus(round(armOcpus))
                    .armMemoryGb(round(armMemoryGb))
                    .bootVolumeGb(bootVolumeGb)
                    .publicIngressRuleCount(publicIngressRuleCount)
                    .highRiskPortRuleCount(highRiskPortRuleCount)
                    .status(highRiskPortRuleCount > 0 ? "HIGH" : publicIngressRuleCount > 0 ? "WARN" : "OK")
                    .message("扫描完成")
                    .build();
        } catch (Exception e) {
            log.warn("OCI risk scan failed for {}", configName, e);
            summary.errorConfigCount++;
            risks.add(risk("ERROR", "CONFIG", "配置扫描失败",
                    "配置 " + configName + " 扫描失败: " + e.getMessage(), configId, configName, region));
            return OciRiskReportRsp.ConfigRisk.builder()
                    .configId(configId)
                    .configName(configName)
                    .region(region)
                    .instanceCount(instanceCount)
                    .runningInstanceCount(runningCount)
                    .armOcpus(round(armOcpus))
                    .armMemoryGb(round(armMemoryGb))
                    .bootVolumeGb(bootVolumeGb)
                    .publicIngressRuleCount(publicIngressRuleCount)
                    .highRiskPortRuleCount(highRiskPortRuleCount)
                    .status("ERROR")
                    .message(e.getMessage())
                    .build();
        }
    }

    private List<BootVolume> safeListBootVolumes(OracleInstanceFetcher fetcher) {
        try {
            List<BootVolume> bootVolumes = fetcher.listBootVolume();
            return bootVolumes == null ? List.of() : bootVolumes;
        } catch (Exception e) {
            log.warn("Boot volume risk scan skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private void addThresholdRisks(List<OciRiskReportRsp.RiskItem> risks,
                                   String configId,
                                   String configName,
                                   String region,
                                   double armOcpus,
                                   double armMemoryGb,
                                   long bootVolumeGb,
                                   int publicIngressRuleCount,
                                   int highRiskPortRuleCount) {
        if (armOcpus > 4 || armMemoryGb > 24) {
            risks.add(risk("WARN", "QUOTA", "ARM 免费资源可能超额",
                    "ARM 当前合计 " + round(armOcpus) + " OCPU / " + round(armMemoryGb) + " GB，请确认是否仍在免费额度内。",
                    configId, configName, region));
        }
        if (bootVolumeGb > 200) {
            risks.add(risk("WARN", "STORAGE", "引导卷容量偏高",
                    "当前引导卷合计 " + bootVolumeGb + " GB，建议清理闲置引导卷或确认计费。",
                    configId, configName, region));
        }
        if (highRiskPortRuleCount > 0) {
            risks.add(risk("HIGH", "NETWORK", "公网高危端口开放",
                    "检测到 " + highRiskPortRuleCount + " 条面向公网的高危端口规则，请优先收敛 SSH/RDP/数据库端口。",
                    configId, configName, region));
        } else if (publicIngressRuleCount > 0) {
            risks.add(risk("WARN", "NETWORK", "公网入站规则较开放",
                    "检测到 " + publicIngressRuleCount + " 条面向公网的入站规则，请确认来源网段是否必要。",
                    configId, configName, region));
        }
    }

    private OciRiskReportRsp.RiskItem risk(String level,
                                           String category,
                                           String title,
                                           String message,
                                           String configId,
                                           String configName,
                                           String region) {
        return OciRiskReportRsp.RiskItem.builder()
                .level(level)
                .category(category)
                .title(title)
                .message(message)
                .configId(configId)
                .configName(configName)
                .region(region)
                .build();
    }

    private boolean isPublic(IngressSecurityRule rule) {
        return "0.0.0.0/0".equals(rule.getSource()) || "::/0".equals(rule.getSource());
    }

    private boolean isHighRiskPublicPort(IngressSecurityRule rule) {
        PortRange range = null;
        if ("6".equals(rule.getProtocol()) && rule.getTcpOptions() != null) {
            range = rule.getTcpOptions().getDestinationPortRange();
        } else if ("17".equals(rule.getProtocol()) && rule.getUdpOptions() != null) {
            range = rule.getUdpOptions().getDestinationPortRange();
        } else if ("all".equalsIgnoreCase(rule.getProtocol())) {
            return true;
        }
        if (range == null) {
            return true;
        }
        Integer min = range.getMin();
        Integer max = range.getMax();
        if (min == null || max == null) {
            return true;
        }
        for (Integer port : HIGH_RISK_PORTS) {
            if (port >= min && port <= max) {
                return true;
            }
        }
        return false;
    }

    private double toDouble(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class SummaryAccumulator {
        int configCount;
        int scannedConfigCount;
        int instanceCount;
        int runningInstanceCount;
        int stoppedInstanceCount;
        int armInstanceCount;
        double armOcpus;
        double armMemoryGb;
        long bootVolumeGb;
        int highRiskCount;
        int warnRiskCount;
        int errorConfigCount;
    }
}
