package com.tony.kingdetective.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.tony.kingdetective.bean.dto.SysUserDTO;
import com.tony.kingdetective.bean.entity.AuditLog;
import com.tony.kingdetective.bean.entity.OciCreateTask;
import com.tony.kingdetective.bean.entity.OciUser;
import com.tony.kingdetective.bean.response.oci.risk.OciRiskReportRsp;
import com.tony.kingdetective.bean.response.ops.SshHostRsp;
import com.tony.kingdetective.bean.vo.SystemDiagnostics;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.IInstanceService;
import com.tony.kingdetective.service.IOciCreateTaskService;
import com.tony.kingdetective.service.IOciUserService;
import com.tony.kingdetective.service.ISysService;
import com.tony.kingdetective.service.SystemDiagnosticsService;
import com.tony.kingdetective.service.ops.SshHostService;
import com.tony.kingdetective.service.oci.ObjectStorageBackupService;
import com.tony.kingdetective.service.oci.OciRiskService;
import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.handler.AbstractCallbackHandler;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.VersionUpdateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tony.kingdetective.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * Telegram operations center entry.
 */
@Slf4j
@Component
public class OpsCenterHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        return buildEditMessage(
                callbackQuery,
                "【运维中心】\n\n集中查看系统诊断、实例概览、任务状态、日志、版本更新，并快速进入常用运维入口。",
                buildOpsKeyboard()
        );
    }

    @Override
    public String getCallbackPattern() {
        return "ops_center";
    }

    static InlineKeyboardMarkup buildOpsKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("系统诊断", "ops_diagnostics"),
                KeyboardBuilder.button("实例概览", "ops_instance_summary")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("任务状态", "ops_task_status"),
                KeyboardBuilder.button("版本更新", "ops_version_status")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("最近日志", "ops_recent_logs"),
                KeyboardBuilder.button("错误日志", "ops_error_logs")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("操作审计", "ops_audit_recent"),
                KeyboardBuilder.button("主机概览", "ops_host_list")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("风险看板", "ops_risk_summary"),
                KeyboardBuilder.button("备份归档", "ops_backup_archive")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("快捷运维", "ops_quick_actions"),
                KeyboardBuilder.button("日志文件", "log_query")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("SSH管理", "ssh_management"),
                KeyboardBuilder.button("安全管理", "security_management")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回主菜单", "back_to_main")));
        rows.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(rows);
    }
}

@Slf4j
@Component
class OpsDiagnosticsHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            SystemDiagnostics diagnostics = SpringUtil.getBean(SystemDiagnosticsService.class).diagnostics();
            long ok = diagnostics.getChecks().stream().filter(item -> "OK".equals(item.getStatus())).count();
            long warn = diagnostics.getChecks().stream().filter(item -> "WARN".equals(item.getStatus())).count();
            long error = diagnostics.getChecks().stream().filter(item -> "ERROR".equals(item.getStatus())).count();

            StringBuilder message = new StringBuilder("【系统诊断】\n\n");
            message.append("总体状态: ").append(diagnostics.getStatus()).append('\n');
            message.append("版本: ").append(OpsCenterSupport.blankToDash(diagnostics.getVersion())).append('\n');
            message.append("Java: ").append(OpsCenterSupport.blankToDash(diagnostics.getJavaVersion())).append('\n');
            message.append("系统: ").append(OpsCenterSupport.blankToDash(diagnostics.getOsName())).append('\n');
            message.append("运行时长: ").append(OpsCenterSupport.formatDuration(diagnostics.getUptimeSeconds())).append('\n');
            message.append("内存: ").append(OpsCenterSupport.formatBytes(diagnostics.getUsedMemoryBytes()))
                    .append(" / ").append(OpsCenterSupport.formatBytes(diagnostics.getMaxMemoryBytes())).append('\n');
            message.append("磁盘可用: ").append(OpsCenterSupport.formatBytes(diagnostics.getFreeDiskBytes())).append('\n');
            message.append("检查项: OK ").append(ok).append(" / WARN ").append(warn).append(" / ERROR ").append(error).append("\n\n");

            diagnostics.getChecks().stream().limit(12).forEach(item -> message.append(item.getStatus())
                    .append(' ').append(item.getName()).append(": ")
                    .append(item.getMessage()).append('\n'));

            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), OpsCenterHandler.buildOpsKeyboard());
        } catch (Exception e) {
            log.error("Telegram system diagnostics failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("系统诊断失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_diagnostics";
    }
}

@Slf4j
@Component
class OpsInstanceSummaryHandler extends AbstractCallbackHandler {

    private static final int MAX_CONFIG_SCAN = 5;

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            ISysService sysService = SpringUtil.getBean(ISysService.class);
            IInstanceService instanceService = SpringUtil.getBean(IInstanceService.class);
            List<SysUserDTO> configs = sysService.list();
            StringBuilder message = new StringBuilder("【实例概览】\n\n");

            if (CollectionUtil.isEmpty(configs)) {
                message.append("暂无 OCI 配置。");
                return buildEditMessage(callbackQuery, message.toString(), OpsCenterHandler.buildOpsKeyboard());
            }

            int scanCount = Math.min(MAX_CONFIG_SCAN, configs.size());
            int total = 0;
            int errors = 0;
            message.append("配置总数: ").append(configs.size())
                    .append("，本次扫描: ").append(scanCount).append(" 个\n")
                    .append("说明: 为避免 Bot 回调超时，仅扫描前 ").append(MAX_CONFIG_SCAN).append(" 个配置。\n\n");

            for (SysUserDTO config : configs.stream().limit(MAX_CONFIG_SCAN).toList()) {
                try {
                    List<SysUserDTO.CloudInstance> instances = instanceService.listRunningInstances(config);
                    total += CollectionUtil.isEmpty(instances) ? 0 : instances.size();
                    appendConfigInstances(message, config, instances);
                } catch (Exception e) {
                    errors++;
                    message.append("- ").append(OpsCenterSupport.configName(config))
                            .append(" / ")
                            .append(OpsCenterSupport.configRegion(config))
                            .append(": 读取失败，").append(OpsCenterSupport.shorten(e.getMessage(), 90))
                            .append('\n');
                }
            }

            message.insert(message.indexOf("\n\n") + 2, "运行实例: " + total + "，读取失败: " + errors + "\n");

            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("刷新实例概览", "ops_instance_summary"),
                    KeyboardBuilder.button("配置列表", "config_list")
            ));
            rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
            rows.add(KeyboardBuilder.buildCancelRow());
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
        } catch (Exception e) {
            log.error("Telegram instance summary failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("实例概览读取失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_instance_summary";
    }

    private void appendConfigInstances(StringBuilder message, SysUserDTO config, List<SysUserDTO.CloudInstance> instances) {
        message.append("- ").append(OpsCenterSupport.configName(config))
                .append(" / ")
                .append(OpsCenterSupport.configRegion(config))
                .append(": ")
                .append(CollectionUtil.isEmpty(instances) ? 0 : instances.size())
                .append(" 台\n");

        if (CollectionUtil.isNotEmpty(instances)) {
            instances.stream().limit(3).forEach(instance -> message.append("  ")
                    .append(OpsCenterSupport.blankToDash(instance.getName()))
                    .append(" / ")
                    .append(OpsCenterSupport.blankToDash(instance.getShape()))
                    .append(" / ")
                    .append(OpsCenterSupport.joinIps(instance.getPublicIp()))
                    .append('\n'));
        }
    }
}

@Slf4j
@Component
class OpsTaskStatusHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        IOciCreateTaskService taskService = SpringUtil.getBean(IOciCreateTaskService.class);
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        List<OciCreateTask> tasks = taskService.list();

        if (CollectionUtil.isEmpty(tasks)) {
            return buildEditMessage(callbackQuery, "【任务状态】\n\n当前没有正在执行的开机任务。", OpsCenterHandler.buildOpsKeyboard());
        }

        Map<String, OciUser> userMap = userService.list().stream()
                .collect(Collectors.toMap(OciUser::getId, item -> item, (left, right) -> left));
        Map<String, Long> architectureCount = tasks.stream()
                .collect(Collectors.groupingBy(task -> OpsCenterSupport.blankToDash(task.getArchitecture()), Collectors.counting()));

        StringBuilder message = new StringBuilder("【任务状态】\n\n");
        message.append("正在执行: ").append(tasks.size()).append(" 个\n");
        architectureCount.forEach((architecture, count) -> message.append(architecture).append(": ").append(count).append(" 个\n"));
        message.append('\n');

        tasks.stream()
                .sorted(Comparator.comparing(OciCreateTask::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(8)
                .forEach(task -> appendTask(message, task, userMap.get(task.getUserId())));

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("刷新状态", "ops_task_status"),
                KeyboardBuilder.button("进入任务管理", "task_management")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
        rows.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
    }

    @Override
    public String getCallbackPattern() {
        return "ops_task_status";
    }

    private void appendTask(StringBuilder message, OciCreateTask task, OciUser user) {
        Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + task.getId());
        message.append("- ")
                .append(user == null ? "未知配置" : user.getUsername())
                .append(" / ")
                .append(user == null ? "-" : user.getOciRegion())
                .append(" / ")
                .append(OpsCenterSupport.blankToDash(task.getArchitecture()))
                .append('\n')
                .append("  规格: ")
                .append(task.getOcpus() == null ? "-" : task.getOcpus().intValue())
                .append("C/")
                .append(task.getMemory() == null ? "-" : task.getMemory().intValue())
                .append("G/")
                .append(task.getDisk() == null ? "-" : task.getDisk())
                .append("G, 数量: ")
                .append(task.getCreateNumbers())
                .append(", 尝试: ")
                .append(counts == null ? 0 : counts)
                .append('\n');
    }
}

@Slf4j
@Component
class OpsVersionStatusHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String current = CommonUtils.getCurrentVersion();
        String latest = StrUtil.blankToDefault(CommonUtils.getLatestVersion(), current);
        boolean hasNewVersion = VersionUpdateUtils.hasNewVersion(current, latest);

        StringBuilder message = new StringBuilder("【版本更新】\n\n");
        message.append("当前版本: ").append(current).append('\n');
        message.append("最新版本: ").append(latest).append('\n');
        message.append("Watcher: ").append(VersionUpdateUtils.isWatcherAlive() ? "可用" : "未检测到").append('\n');
        message.append("状态: ").append(hasNewVersion ? "发现新版本，可点击更新。" : "当前已是最新版本。").append('\n');
        message.append("触发文件: ").append(VersionUpdateUtils.TRIGGER_FILE_PATH).append('\n');

        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (hasNewVersion) {
            rows.add(new InlineKeyboardRow(KeyboardBuilder.button("立即更新", "update_sys_version")));
        }
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("重新检测", "ops_version_status"),
                KeyboardBuilder.button("版本详情", "version_info")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
        rows.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(message.toString()), new InlineKeyboardMarkup(rows));
    }

    @Override
    public String getCallbackPattern() {
        return "ops_version_status";
    }
}

@Slf4j
@Component
class OpsRecentLogsHandler extends AbstractCallbackHandler {

    private static final int MAX_LINES = 30;

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        File logFile = new File(CommonUtils.LOG_FILE_PATH);
        if (!logFile.exists()) {
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("【最近日志】\n\n日志文件不存在: " + CommonUtils.LOG_FILE_PATH), OpsCenterHandler.buildOpsKeyboard());
        }

        List<String> lines = readLastLines(logFile, MAX_LINES);
        StringBuilder message = new StringBuilder("【最近日志】\n");
        message.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        if (lines.isEmpty()) {
            message.append("暂无日志内容。");
        } else {
            lines.forEach(line -> message.append(OpsCenterSupport.shorten(line, 180)).append('\n'));
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("刷新", "ops_recent_logs"),
                KeyboardBuilder.button("发送日志文件", "log_query")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
        rows.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
    }

    @Override
    public String getCallbackPattern() {
        return "ops_recent_logs";
    }

    static List<String> readLastLines(File logFile, int limit) {
        LinkedList<String> lines = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > limit) {
                    lines.removeFirst();
                }
            }
        } catch (Exception e) {
            log.error("Read recent log failed", e);
            lines.clear();
            lines.add("读取日志失败: " + e.getMessage());
        }
        return lines;
    }
}

@Slf4j
@Component
class OpsErrorLogsHandler extends AbstractCallbackHandler {

    private static final int MAX_LINES = 25;

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        File logFile = new File(CommonUtils.LOG_FILE_PATH);
        if (!logFile.exists()) {
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("【错误日志】\n\n日志文件不存在: " + CommonUtils.LOG_FILE_PATH), OpsCenterHandler.buildOpsKeyboard());
        }

        List<String> importantLines = OpsRecentLogsHandler.readLastLines(logFile, 500).stream()
                .filter(this::isImportant)
                .toList();
        List<String> lines = importantLines.stream()
                .skip(Math.max(0, importantLines.size() - MAX_LINES))
                .toList();

        StringBuilder message = new StringBuilder("【错误日志】\n");
        message.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        if (lines.isEmpty()) {
            message.append("最近没有 WARN / ERROR / Exception 日志。");
        } else {
            lines.forEach(line -> message.append(OpsCenterSupport.shorten(line, 180)).append('\n'));
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("刷新错误日志", "ops_error_logs"),
                KeyboardBuilder.button("完整日志文件", "log_query")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
        rows.add(KeyboardBuilder.buildCancelRow());
        return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
    }

    @Override
    public String getCallbackPattern() {
        return "ops_error_logs";
    }

    private boolean isImportant(String line) {
        return line != null && (line.contains(" WARN ") || line.contains(" ERROR ") || line.contains("Exception"));
    }
}

@Slf4j
@Component
class OpsAuditRecentHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            IAuditLogService auditLogService = SpringUtil.getBean(IAuditLogService.class);
            List<AuditLog> logs = auditLogService.recent(10);

            StringBuilder message = new StringBuilder("【操作审计】\n\n");
            if (CollectionUtil.isEmpty(logs)) {
                message.append("暂无审计记录。");
            } else {
                logs.forEach(log -> message.append(log.getSuccess() != null && log.getSuccess() ? "OK " : "FAIL ")
                        .append(OpsCenterSupport.formatTime(log.getCreateTime()))
                        .append(" / ")
                        .append(OpsCenterSupport.blankToDash(log.getOperation()))
                        .append(" / ")
                        .append(OpsCenterSupport.blankToDash(log.getTarget()))
                        .append('\n')
                        .append("  ")
                        .append(OpsCenterSupport.blankToDash(log.getDetails() == null ? log.getErrorMessage() : log.getDetails()))
                        .append('\n'));
            }

            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("刷新审计", "ops_audit_recent"),
                    KeyboardBuilder.button("返回运维中心", "ops_center")
            ));
            rows.add(KeyboardBuilder.buildCancelRow());
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
        } catch (Exception e) {
            log.error("Telegram audit query failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("操作审计读取失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_audit_recent";
    }
}

@Slf4j
@Component
class OpsHostListHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            SshHostService sshHostService = SpringUtil.getBean(SshHostService.class);
            List<SshHostRsp> hosts = sshHostService.list(null);

            StringBuilder message = new StringBuilder("【SSH 主机概览】\n\n");
            if (CollectionUtil.isEmpty(hosts)) {
                message.append("暂无保存的 SSH 主机。");
            } else {
                message.append("已保存主机: ").append(hosts.size()).append(" 台\n\n");
                hosts.stream().limit(10).forEach(host -> message.append("- ")
                        .append(OpsCenterSupport.blankToDash(host.getName()))
                        .append(" / ")
                        .append(OpsCenterSupport.blankToDash(host.getUsername()))
                        .append('@')
                        .append(OpsCenterSupport.blankToDash(host.getHost()))
                        .append(':')
                        .append(host.getPort() == null ? 22 : host.getPort())
                        .append('\n')
                        .append("  认证: ")
                        .append(OpsCenterSupport.blankToDash(host.getAuthType()))
                        .append(", 标签: ")
                        .append(OpsCenterSupport.blankToDash(host.getTags()))
                        .append(", 最近使用: ")
                        .append(OpsCenterSupport.formatTime(host.getLastUsedAt()))
                        .append('\n'));
            }

            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("刷新主机", "ops_host_list"),
                    KeyboardBuilder.button("进入 SSH 管理", "ssh_management")
            ));
            rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
            rows.add(KeyboardBuilder.buildCancelRow());
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
        } catch (Exception e) {
            log.error("Telegram SSH host list failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("主机概览读取失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_host_list";
    }
}

@Slf4j
@Component
class OpsRiskSummaryHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            OciRiskService riskService = SpringUtil.getBean(OciRiskService.class);
            OciRiskReportRsp report = riskService.report(5);
            OciRiskReportRsp.Summary summary = report.getSummary();

            StringBuilder message = new StringBuilder("【OCI 风险看板】\n\n");
            message.append("扫描配置: ").append(summary.getScannedConfigCount()).append('/').append(summary.getConfigCount()).append('\n');
            message.append("实例: ").append(summary.getRunningInstanceCount()).append(" 运行 / ")
                    .append(summary.getInstanceCount()).append(" 总计\n");
            message.append("ARM: ").append(summary.getArmOcpus()).append(" OCPU / ")
                    .append(summary.getArmMemoryGb()).append(" GB\n");
            message.append("引导卷: ").append(summary.getBootVolumeGb()).append(" GB\n");
            message.append("风险: HIGH ").append(summary.getHighRiskCount())
                    .append(" / WARN ").append(summary.getWarnRiskCount())
                    .append(" / ERROR ").append(summary.getErrorConfigCount()).append("\n\n");

            if (CollectionUtil.isEmpty(report.getRisks())) {
                message.append("当前扫描范围未发现高优先级风险。");
            } else {
                report.getRisks().stream().limit(8).forEach(risk -> message.append(risk.getLevel())
                        .append(" / ")
                        .append(risk.getCategory())
                        .append(" / ")
                        .append(risk.getTitle())
                        .append('\n')
                        .append("  ")
                        .append(OpsCenterSupport.blankToDash(risk.getConfigName()))
                        .append(" / ")
                        .append(OpsCenterSupport.blankToDash(risk.getRegion()))
                        .append(": ")
                        .append(OpsCenterSupport.shorten(risk.getMessage(), 150))
                        .append('\n'));
            }

            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("刷新风险看板", "ops_risk_summary"),
                    KeyboardBuilder.button("系统诊断", "ops_diagnostics")
            ));
            rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
            rows.add(KeyboardBuilder.buildCancelRow());
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
        } catch (Exception e) {
            log.error("Telegram OCI risk summary failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("风险看板读取失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_risk_summary";
    }
}

@Slf4j
@Component
class OpsBackupArchiveHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            ObjectStorageBackupService backupService = SpringUtil.getBean(ObjectStorageBackupService.class);
            List<String> backups = backupService.listLocalBackups(8);

            StringBuilder message = new StringBuilder("【备份归档】\n\n");
            message.append("Web 端已支持本地备份包创建、OCI Object Storage 上传、归档列表刷新和对象删除。\n");
            message.append("TG 端保留为状态入口，涉及 Bucket 选择和删除等动作建议在 Web 控制台完成。\n\n");
            message.append("本地备份目录: /app/king-detective/backups\n");
            if (CollectionUtil.isEmpty(backups)) {
                message.append("最近本地备份: 暂无\n");
            } else {
                message.append("最近本地备份:\n");
                backups.forEach(item -> message.append("- ").append(item).append('\n'));
            }

            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("备份恢复", "backup_restore"),
                    KeyboardBuilder.button("系统诊断", "ops_diagnostics")
            ));
            rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
            rows.add(KeyboardBuilder.buildCancelRow());
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown(OpsCenterSupport.limitTelegramText(message.toString())), new InlineKeyboardMarkup(rows));
        } catch (Exception e) {
            log.error("Telegram backup archive failed", e);
            return buildEditMessage(callbackQuery, OpsCenterSupport.escapeMarkdown("备份归档读取失败: " + e.getMessage()), OpsCenterHandler.buildOpsKeyboard());
        }
    }

    @Override
    public String getCallbackPattern() {
        return "ops_backup_archive";
    }
}

@Component
class OpsQuickActionsHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("一键抢机", "config_list"),
                KeyboardBuilder.button("快捷开机", "quick_start")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("一键测活", "check_alive"),
                KeyboardBuilder.button("任务管理", "task_management")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("实例概览", "ops_instance_summary"),
                KeyboardBuilder.button("开放端口", "open_all_ports_select")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("SSH管理", "ssh_management"),
                KeyboardBuilder.button("安全管理", "security_management")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("系统诊断", "ops_diagnostics"),
                KeyboardBuilder.button("错误日志", "ops_error_logs")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("风险看板", "ops_risk_summary"),
                KeyboardBuilder.button("备份归档", "ops_backup_archive")
        ));
        rows.add(new InlineKeyboardRow(
                KeyboardBuilder.button("版本更新", "ops_version_status"),
                KeyboardBuilder.button("备份恢复", "backup_restore")
        ));
        rows.add(new InlineKeyboardRow(KeyboardBuilder.button("返回运维中心", "ops_center")));
        rows.add(KeyboardBuilder.buildCancelRow());

        return buildEditMessage(
                callbackQuery,
                "【快捷运维入口】\n\n这里集中放置高频、安全的运维入口。危险动作仍沿用原有确认流程。",
                new InlineKeyboardMarkup(rows)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "ops_quick_actions";
    }
}

final class OpsCenterSupport {

    private OpsCenterSupport() {
    }

    static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static String configName(SysUserDTO config) {
        return config == null ? "-" : blankToDash(config.getUsername());
    }

    static String configRegion(SysUserDTO config) {
        return config == null || config.getOciCfg() == null ? "-" : blankToDash(config.getOciCfg().getRegion());
    }

    static String joinIps(List<String> ips) {
        if (CollectionUtil.isEmpty(ips)) {
            return "-";
        }
        return ips.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining(", "));
    }

    static String formatDuration(Long seconds) {
        if (seconds == null || seconds < 0) {
            return "-";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "天" + hours + "小时";
        }
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    static String formatBytes(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "-";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }

    static String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    static String limitTelegramText(String text) {
        if (text == null || text.length() <= 3800) {
            return text;
        }
        return text.substring(0, 3760) + "\n\n内容过长，已截断。";
    }

    static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("`", "\\`");
    }
}
