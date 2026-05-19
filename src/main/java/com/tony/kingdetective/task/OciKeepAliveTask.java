package com.tony.kingdetective.task;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.bean.entity.SshHost;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.mapper.SshHostMapper;
import com.tony.kingdetective.service.IOciKvService;
import com.tony.kingdetective.service.ops.SecretCryptoService;
import com.tony.kingdetective.telegram.service.SshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OCI 实例防回收保活任务
 * <p>
 * Oracle Cloud 对长期 CPU 使用率低于 15% 的 Always Free 实例存在强制回收机制。
 * 本任务每 60 分钟通过 SSH 登录已注册的实例，执行一段轻量的 CPU 运算命令，
 * 使实例保持适量的 CPU 活跃度，从而避免被 Oracle 判定为"闲置"并回收。
 * <p>
 * 保活命令说明：
 * - 使用 bc 计算高精度圆周率（非常 CPU 密集，持续约 10 秒）
 * - 产生真实 CPU 峰值，满足 Oracle 的活跃度要求
 * - 命令执行完毕后自动退出，不会长期占用资源
 *
 * @author Tony Wang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OciKeepAliveTask {

    private final IOciKvService kvService;
    private final SshHostMapper sshHostMapper;
    private final SshService sshService;
    private final SecretCryptoService secretCryptoService;

    // 保活命令：计算圆周率到小数点后 5000 位，持续产生 CPU 负载（约 10~30 秒）
    private static final String KEEP_ALIVE_CMD =
            "timeout 30 bash -c 'echo \"scale=5000; 4*a(1)\" | bc -l > /dev/null 2>&1' & echo OK";

    /**
     * 每 60 分钟执行一次保活心跳
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void runKeepAlive() {
        if (!isKeepAliveEnabled()) {
            return;
        }

        List<SshHost> hosts = sshHostMapper.selectList(new LambdaQueryWrapper<>());
        if (hosts == null || hosts.isEmpty()) {
            log.debug("KeepAlive: No SSH hosts registered, skipping.");
            return;
        }

        log.info("OCI KeepAlive: Starting heartbeat for {} registered hosts...", hosts.size());

        for (SshHost host : hosts) {
            try {
                String password = secretCryptoService.decrypt(host.getPasswordCipher());
                String result = sshService.executeCommand(
                        host.getHost(),
                        host.getPort() != null ? host.getPort() : 22,
                        host.getUsername(),
                        password,
                        KEEP_ALIVE_CMD
                );
                if (result != null && !result.startsWith("❌")) {
                    log.info("KeepAlive: ✅ Host [{}] ({}) heartbeat sent.", host.getName(), host.getHost());
                } else {
                    log.warn("KeepAlive: ⚠️ Host [{}] ({}) returned unexpected result: {}", host.getName(), host.getHost(), result);
                }
            } catch (Exception e) {
                log.warn("KeepAlive: ❌ Failed to send heartbeat to host [{}] ({}): {}", host.getName(), host.getHost(), e.getMessage());
            }
        }

        log.info("OCI KeepAlive: Heartbeat round completed.");
    }

    private boolean isKeepAliveEnabled() {
        OciKv kv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.ENABLE_OCI_KEEP_ALIVE.getCode()));
        return kv != null && "true".equalsIgnoreCase(kv.getValue());
    }
}
