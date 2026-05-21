package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rescue")  // fix #15: 统一路径风格，去掉 /v1 版本前缀
public class RescueCenterController {

    @Value("${king-detective.app-dir:${KING_DETECTIVE_APP_DIR:/app/king-detective}}")
    private String appDir;

    @GetMapping("/overview")
    public ResponseData<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", CommonUtils.getCurrentVersion());
        data.put("appDir", appDir);
        data.put("lightRescue", lightRescueItems());
        data.put("bootVolumeRescue", bootVolumeSteps());
        data.put("netbootXyz", netbootPlan());
        data.put("localScripts", localScripts());
        data.put("warning", "救援中心默认只生成检查和操作向导。停止实例、拆卷、改 bootloader、重装系统等动作必须人工确认。");
        return ResponseData.successData(data);
    }

    @GetMapping("/light-script")
    public ResponseData<String> lightScript() {
        return ResponseData.successData("""
                #!/bin/bash
                set -Eeuo pipefail

                echo "=== W-探长轻量自救检查 ==="
                date
                echo

                echo "[系统]"
                uname -a || true
                uptime || true
                free -h || true
                df -h || true
                echo

                echo "[SSH]"
                systemctl status ssh --no-pager 2>/dev/null || systemctl status sshd --no-pager 2>/dev/null || true
                ss -lntp 2>/dev/null | grep -E ':22\\b|:9527\\b' || true
                echo

                echo "[网络]"
                ip addr || true
                ip route || true
                resolvectl status 2>/dev/null || cat /etc/resolv.conf || true
                echo

                echo "[防火墙]"
                ufw status verbose 2>/dev/null || true
                iptables -S 2>/dev/null | head -80 || true
                echo

                echo "[cloud-init]"
                cloud-init status --long 2>/dev/null || true
                journalctl -u cloud-init --no-pager -n 80 2>/dev/null || true
                echo

                echo "[最近错误]"
                journalctl -p warning..alert --no-pager -n 120 2>/dev/null || true
                echo "=== 检查完成 ==="
                """, "请求成功");
    }

    @GetMapping("/netboot-script")
    public ResponseData<String> netbootScript(@RequestParam(value = "mode", required = false, defaultValue = "ipxe") String mode) {
        String normalizedMode = mode == null ? "ipxe" : mode.trim().toLowerCase();
        String script = """
                #!/bin/bash
                set -Eeuo pipefail

                echo "=== netboot.xyz 实验区 ==="
                echo "此脚本只做环境检查和引导方案提示，不会自动覆盖启动盘。"
                echo "推荐先确保有 OCI 控制台连接、boot volume 备份和可回滚方案。"
                echo

                echo "[固件/启动环境]"
                [ -d /sys/firmware/efi ] && echo "UEFI: yes" || echo "UEFI: no/unknown"
                command -v grub-editenv >/dev/null 2>&1 && grub-editenv list || true
                echo

                echo "[netboot.xyz iPXE 链接]"
                echo "Legacy/BIOS iPXE: https://boot.netboot.xyz/ipxe/netboot.xyz.lkrn"
                echo "UEFI iPXE:       https://boot.netboot.xyz/ipxe/netboot.xyz.efi"
                echo "iPXE script:     https://boot.netboot.xyz"
                echo

                echo "[下一步]"
                echo "1. 能 SSH 登录的机器：建议先在 Web SSH 内运行轻量自救检查。"
                echo "2. SSH 失联机器：优先使用 OCI boot volume 拆卷救援流程。"
                echo "3. 确认控制台可用后，再人工配置一次性 iPXE/netboot 引导。"
                """;
        if ("grub".equals(normalizedMode)) {
            script += """

                    echo
                    echo "[GRUB 提示]"
                    echo "不同发行版和 UEFI/BIOS 的 GRUB 链式引导方式不同，本项目暂不自动写入 grub.cfg。"
                    echo "后续可在测试机验证后，按系统类型生成一次性 grub-reboot 菜单。"
                    """;
        }
        return ResponseData.successData(script, "请求成功");
    }

    private List<Map<String, String>> lightRescueItems() {
        return List.of(
                item("SSH 检查", "检查 ssh/sshd 服务、22 端口监听、防火墙和 authorized_keys。"),
                item("磁盘检查", "检查根分区、inode、日志目录和 Docker 占用，避免磁盘满导致服务异常。"),
                item("网络检查", "检查网卡、路由、DNS、OCI 内网地址和面板端口。"),
                item("cloud-init 检查", "检查 cloud-init 状态，定位首次启动卡死或密钥注入失败。"),
                item("错误日志", "汇总 journalctl 最近 warning/error，便于远程判断失联原因。")
        );
    }

    private List<Map<String, String>> bootVolumeSteps() {
        return List.of(
                item("1. 停止原实例", "在 OCI 控制台或 API 停止失联实例，避免文件系统继续写入。"),
                item("2. 备份 boot volume", "先创建 boot volume 备份，所有修复动作必须可回滚。"),
                item("3. 拆下 boot volume", "将原实例 boot volume 分离，记录原始实例、区域和可用域。"),
                item("4. 挂载到救援机", "挂载到同可用域的健康 Linux 实例，执行 fsck、挂载和配置修复。"),
                item("5. 修复并挂回", "修复 ssh/network/cloud-init/fstab 后卸载，挂回原实例启动验证。")
        );
    }

    private List<Map<String, String>> netbootPlan() {
        return List.of(
                item("定位", "netboot.xyz 适合作为网络安装/救援入口，但在 OCI 普通实例上不应默认自动写 bootloader。"),
                item("首期能力", "W-探长提供检查脚本、链接、步骤和实验区说明，避免误清盘。"),
                item("后续能力", "按 AMD/ARM、Ubuntu/Oracle Linux、UEFI/BIOS 实测后，再开放一次性引导按钮。")
        );
    }

    private List<Map<String, Object>> localScripts() {
        return List.of(
                script("backup.sh", "创建本地备份包", exists("scripts/backup.sh")),
                script("restore.sh", "从备份包恢复", exists("scripts/restore.sh")),
                script("support-bundle.sh", "生成故障支持包", exists("scripts/support-bundle.sh")),
                script("server-smoke-test.sh", "部署后体检", exists("scripts/server-smoke-test.sh"))
        );
    }

    private boolean exists(String relativePath) {
        return new File(appDir, relativePath).exists();
    }

    private Map<String, String> item(String title, String description) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("description", description);
        return item;
    }

    private Map<String, Object> script(String name, String description, boolean exists) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", description);
        item.put("exists", exists);
        return item;
    }
}
