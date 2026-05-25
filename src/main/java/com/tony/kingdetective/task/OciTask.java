package com.tony.kingdetective.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oracle.bmc.Region;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.tony.kingdetective.bean.constant.CacheConstant;
import com.tony.kingdetective.bean.dto.SysUserDTO;
import com.tony.kingdetective.bean.entity.IpData;
import com.tony.kingdetective.bean.entity.OciCreateTask;
import com.tony.kingdetective.bean.entity.OciKv;
import com.tony.kingdetective.bean.entity.OciUser;
import com.tony.kingdetective.config.OracleInstanceFetcher;
import com.tony.kingdetective.enums.EnableEnum;
import com.tony.kingdetective.enums.OciUnSupportRegionEnum;
import com.tony.kingdetective.enums.SysCfgEnum;
import com.tony.kingdetective.enums.SysCfgTypeEnum;
import com.tony.kingdetective.service.*;
import com.tony.kingdetective.telegram.TgBot;
import com.tony.kingdetective.utils.CommonUtils;
import com.tony.kingdetective.utils.SQLiteHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tony.kingdetective.service.impl.OciServiceImpl.*;

/**
 * <p>
 * OciTask
 * </p >
 *
 * @author yohann
 * @since 2024/11/1 19:21
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "king-detective.startup", name = "tasks-enabled", havingValue = "true", matchIfMissing = true)
public class OciTask implements ApplicationRunner {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private IOciUserService userService;
    @Resource
    private IOciKvService kvService;
    @Resource
    private ISysService sysService;
    @Resource
    private IIpDataService ipDataService;
    @Resource
    private IOciCreateTaskService createTaskService;
    @Resource
    private TaskScheduler taskScheduler;
    @Resource
    private SQLiteHelper sqLiteHelper;
    @Resource
    private ExecutorService virtualExecutor;

    private static volatile boolean isPushedLatestVersion = false;
    public static volatile TelegramBotsLongPollingApplication botsApplication;

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;
    @Value("${telegram.bot.token:${TELEGRAM_BOT_TOKEN:${BOT_TOKEN:}}}")
    private String telegramBotToken;
    @Value("${telegram.bot.chat-id:${TELEGRAM_BOT_CHAT_ID:${TELEGRAM_CHAT_ID:${TG_CHAT_ID:}}}}")
    private String telegramChatId;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        TEMP_MAP.put("password", password);
        startTgBog();
        updateUserInDb();
        cleanLogTask();
        cleanAndRestartTask();
        initGenMfaPng();
        saveVersion();
        startInform();
        pushVersionUpdateMsg(kvService, sysService);
        dailyBroadcastTask();
        supportOciUnknownRegionTask();
        initMapData();
    }

    private void startTgBog() {
        virtualExecutor.execute(() -> {
            OciKv tgToken = getSingleKv(kvService, SysCfgEnum.SYS_TG_BOT_TOKEN, null, null);
            OciKv tgChatId = getSingleKv(kvService, SysCfgEnum.SYS_TG_CHAT_ID, null, null);
            String token = firstNonBlank(kvValue(tgToken), telegramBotToken);
            String chatId = firstNonBlank(kvValue(tgChatId), telegramChatId);
            if (StrUtil.isBlank(token) && StrUtil.isBlank(chatId)) {
                log.info("TG Bot token and chat ID are not configured, skipping TG Bot startup");
                return;
            }
            if (StrUtil.isBlank(token) || StrUtil.isBlank(chatId)) {
                log.warn("TG Bot config is incomplete, tokenConfigured={}, chatIdConfigured={}",
                        StrUtil.isNotBlank(token), StrUtil.isNotBlank(chatId));
                return;
            }
            if (StrUtil.isNotBlank(token) && StrUtil.isNotBlank(chatId)) {
                botsApplication = new TelegramBotsLongPollingApplication();
                try {
                    botsApplication.registerBot(token, new TgBot(token, chatId));
                    log.info("TG Bot successfully started with chatId: {}", chatId);
                } catch (Exception e) {
                    log.error("Failed to start TG Bot", e);
                }
                // Virtual thread continues to run, no need for join()
            }
        });
    }

    private static String kvValue(OciKv kv) {
        return kv == null ? null : kv.getValue();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static OciKv getSingleKv(IOciKvService kvService, SysCfgEnum cfgEnum, SysCfgTypeEnum typeEnum, String preferredValue) {
        LambdaQueryWrapper<OciKv> wrapper = new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, cfgEnum.getCode());
        if (typeEnum != null) {
            wrapper.eq(OciKv::getType, typeEnum.getCode());
        }
        wrapper.orderByDesc(OciKv::getCreateTime)
                .orderByDesc(OciKv::getId);

        List<OciKv> records = kvService.list(wrapper);
        if (CollectionUtil.isEmpty(records)) {
            return null;
        }

        OciKv selected = records.get(0);
        if (StrUtil.isNotBlank(preferredValue)) {
            selected = records.stream()
                    .filter(record -> preferredValue.equals(record.getValue()))
                    .findFirst()
                    .orElse(selected);
        }

        if (records.size() > 1) {
            String selectedId = selected.getId();
            List<String> duplicateIds = records.stream()
                    .map(OciKv::getId)
                    .filter(StrUtil::isNotBlank)
                    .filter(id -> !Objects.equals(id, selectedId))
                    .toList();
            if (CollectionUtil.isNotEmpty(duplicateIds)) {
                kvService.removeByIds(duplicateIds);
                log.warn("Duplicate OciKv records cleaned, code={}, type={}, kept={}, removed={}",
                        cfgEnum.getCode(), typeEnum == null ? "*" : typeEnum.getCode(), selectedId, duplicateIds.size());
            }
        }

        return selected;
    }

    private static String getSingleKvValue(IOciKvService kvService, SysCfgEnum cfgEnum, SysCfgTypeEnum typeEnum, String preferredValue) {
        return kvValue(getSingleKv(kvService, cfgEnum, typeEnum, preferredValue));
    }

    private void cleanLogTask() {
        addAtFixedRateTask(account, () -> {
            FileUtil.writeUtf8String("", CommonUtils.LOG_FILE_PATH);
            log.info("【日志清理任务】日志文件：{} 已清空", CommonUtils.LOG_FILE_PATH);
        }, 8, 8, TimeUnit.HOURS);
    }

    private void updateUserInDb() {
        sqLiteHelper.addColumnIfNotExists("oci_user", "tenant_name", "VARCHAR(64) NULL");
        sqLiteHelper.addColumnIfNotExists("oci_create_task", "oci_region", "VARCHAR(64) NULL");
        sqLiteHelper.addColumnIfNotExists("oci_user", "tenant_create_time", "datetime NULL");
        virtualExecutor.execute(() -> {
            List<OciUser> ociUsers = userService.list(new LambdaQueryWrapper<OciUser>()
                    .isNull(OciUser::getTenantCreateTime)
                    .or()
                    .isNull(OciUser::getTenantName)
                    .or()
                    .eq(OciUser::getTenantName, "")
            );
            if (CollectionUtil.isNotEmpty(ociUsers)) {
                userService.updateBatchById(ociUsers.parallelStream().peek(x -> {
                    SysUserDTO sysUserDTO = sysService.getOciUser(x.getId());
                    try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                        Tenancy tenancy = fetcher.getIdentityClient().getTenancy(GetTenancyRequest.builder()
                                .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                .build()).getTenancy();
                        x.setTenantName(tenancy.getName());
                        x.setTenantCreateTime(LocalDateTime.parse(fetcher.getRegisteredTime(), CommonUtils.DATETIME_FMT_NORM));
                    } catch (Exception e) {
                        log.error("更新配置：{} 失败", x.getUsername());
                    }
                }).collect(Collectors.toList()));
            }
        });
    }

    private void cleanAndRestartTask() {
        virtualExecutor.execute(() -> {
            Random random = new Random();
            Optional.ofNullable(createTaskService.list())
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList)
                    .forEach(task -> {
                        // 随机延迟 5~10 秒
                        int delay = 5 + random.nextInt(6);
                        CREATE_INSTANCE_POOL.schedule(() -> {
                            if (task.getCreateNumbers() <= 0) {
                                createTaskService.removeById(task.getId());
                            } else {
                                OciUser ociUser = userService.getById(task.getUserId());
                                SysUserDTO sysUserDTO = SysUserDTO.builder()
                                        .ociCfg(SysUserDTO.OciCfg.builder()
                                                .userId(ociUser.getOciUserId())
                                                .tenantId(ociUser.getOciTenantId())
                                                .region(StrUtil.isBlank(task.getOciRegion()) ? ociUser.getOciRegion() : task.getOciRegion())
                                                .fingerprint(ociUser.getOciFingerprint())
                                                .privateKeyPath(ociUser.getOciKeyPath())
                                                .privateKey(ociUser.getPrivateKey())
                                                .build())
                                        .taskId(task.getId())
                                        .username(ociUser.getUsername())
                                        .ocpus(task.getOcpus())
                                        .memory(task.getMemory())
                                        .disk(task.getDisk().equals(50) ? null : Long.valueOf(task.getDisk()))
                                        .architecture(task.getArchitecture())
                                        .interval(Long.valueOf(task.getInterval()))
                                        .createNumbers(task.getCreateNumbers())
                                        .operationSystem(task.getOperationSystem())
                                        .rootPassword(task.getRootPassword())
                                        .build();
                                addTask(CommonUtils.CREATE_TASK_PREFIX + task.getId(), () ->
                                                execCreate(sysUserDTO, sysService, instanceService, createTaskService),
                                        0, task.getInterval(), TimeUnit.SECONDS);
                            }
                        }, delay, TimeUnit.SECONDS);
                    });
        });
    }

    private void initGenMfaPng() {
        virtualExecutor.execute(() -> {
            Optional.ofNullable(getSingleKv(kvService, SysCfgEnum.SYS_MFA_SECRET, null, null)).ifPresent(mfa -> {
                String qrCodeURL = CommonUtils.generateQRCodeURL(mfa.getValue(), account, "king-detective");
                CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
            });
        });
    }

    private void saveVersion() {
        try {
            String currentVersion = CommonUtils.getCurrentVersion();
            OciKv oldVersion = getSingleKv(kvService, SysCfgEnum.SYS_INFO_VERSION, SysCfgTypeEnum.SYS_INFO, currentVersion);
            if (null == oldVersion) {
                kvService.save(OciKv.builder()
                        .id(IdUtil.getSnowflake().nextIdStr())
                        .code(SysCfgEnum.SYS_INFO_VERSION.getCode())
                        .type(SysCfgTypeEnum.SYS_INFO.getCode())
                        .value(currentVersion)
                        .build());
                log.info("版本信息已初始化：{}", currentVersion);
            } else if (!currentVersion.equals(oldVersion.getValue())) {
                String previousVersion = oldVersion.getValue();
                oldVersion.setValue(currentVersion);
                kvService.updateById(oldVersion);
                log.info("版本信息已更新：{} -> {}", previousVersion, currentVersion);
            }
        } catch (Exception e) {
            log.error("Failed to save application version", e);
        }

    }

    private void startInform() {
        String latestVersion = CommonUtils.getLatestVersion();
        String nowVersion = getSingleKvValue(kvService, SysCfgEnum.SYS_INFO_VERSION, SysCfgTypeEnum.SYS_INFO, CommonUtils.getCurrentVersion());
        nowVersion = StrUtil.blankToDefault(nowVersion, CommonUtils.getCurrentVersion());
        latestVersion = StrUtil.blankToDefault(latestVersion, nowVersion);
        log.info(String.format("【king-detective】服务启动成功~ 当前版本：%s 最新版本：%s", nowVersion, latestVersion));
        sysService.sendMessage(String.format("【king-detective】服务启动成功🎉🎉\n\n当前版本：%s\n最新版本：%s\n发送 /start 操作机器人🤖\n放货通知频道：https://t.me/Woci_detective", nowVersion, latestVersion));
    }

    public static void pushVersionUpdateMsg(IOciKvService kvService, ISysService sysService) {
        String taskId = CacheConstant.PREFIX_PUSH_VERSION_UPDATE_MSG;

        addTask(taskId, () -> {
            OciKv evun = getSingleKv(kvService, SysCfgEnum.ENABLED_VERSION_UPDATE_NOTIFICATIONS, null, null);
            if (null != evun && evun.getValue().equals(EnableEnum.OFF.getCode())) {
                return;
            }
            String latest = CommonUtils.getLatestVersion();
            String now = getSingleKvValue(kvService, SysCfgEnum.SYS_INFO_VERSION, SysCfgTypeEnum.SYS_INFO, CommonUtils.getCurrentVersion());
            if (StrUtil.isBlank(latest)) {
                return;
            }
            if (StrUtil.isNotBlank(now) && !now.equals(latest)) {
                log.warn(String.format("【king-detective】版本更新啦！！！当前版本：%s 最新版本：%s", now, latest));
                if (!isPushedLatestVersion) {
                    sysService.sendVersionUpdateMessage(now, latest, CommonUtils.getLatestVersionBody());
                    isPushedLatestVersion = true;
                }
            }
        }, 0, 1, TimeUnit.DAYS);

        addTask(taskId + "_push", () -> {
            OciKv evun = getSingleKv(kvService, SysCfgEnum.ENABLED_VERSION_UPDATE_NOTIFICATIONS, null, null);
            if (null != evun && evun.getValue().equals(EnableEnum.OFF.getCode())) {
                return;
            }
            isPushedLatestVersion = false;
        }, 12, 12, TimeUnit.HOURS);
    }

    private void dailyBroadcastTask() {
        OciKv edb = getSingleKv(kvService, SysCfgEnum.ENABLE_DAILY_BROADCAST, null, null);
        OciKv dbc = getSingleKv(kvService, SysCfgEnum.DAILY_BROADCAST_CRON, null, null);
        if (null != edb && edb.getValue().equals(EnableEnum.OFF.getCode())) {
            return;
        }

        ScheduledFuture<?> scheduled = taskScheduler.schedule(() -> {
            String message = "【每日播报】\n" +
                    "\n" +
                    "\uD83D\uDD58 时间：\t%s\n" +
                    "\uD83D\uDD11 总API配置数：\t%s\n" +
                    "❌ 失效API配置数：\t%s\n" +
                    "⚠\uFE0F 失效的API配置：\t\n- %s\n" +
                    "\uD83D\uDECE 正在执行的开机任务：\n" +
                    "%s\n";
            List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                    .isNotNull(OciUser::getId)
                    .select(OciUser::getId), String::valueOf);

            CompletableFuture<List<String>> fails = CompletableFuture.supplyAsync(() -> {
                if (ids.isEmpty()) {
                    return Collections.emptyList();
                }
                return ids.parallelStream().filter(id -> {
                    SysUserDTO ociUser = sysService.getOciUser(id);
                    try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                        fetcher.getAvailabilityDomains();
                    } catch (Exception e) {
                        return true;
                    }
                    return false;
                }).map(id -> sysService.getOciUser(id).getUsername()).collect(Collectors.toList());
            }, virtualExecutor);

            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                List<OciCreateTask> ociCreateTaskList = createTaskService.list();
                if (ociCreateTaskList.isEmpty()) {
                    return "无";
                }
                String template = "[%s] [%s] [%s] [%s核/%sGB/%sGB] [%s台] [%s] [%s次]";
                return ociCreateTaskList.parallelStream().map(x -> {
                    OciUser ociUser = userService.getById(x.getUserId());
                    Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
                    return String.format(template, ociUser.getUsername(), ociUser.getOciRegion(), x.getArchitecture(),
                            x.getOcpus().longValue(), x.getMemory().longValue(), x.getDisk(), x.getCreateNumbers(),
                            CommonUtils.getTimeDifference(x.getCreateTime()), counts == null ? "0" : counts);
                }).collect(Collectors.joining("\n"));
            }, virtualExecutor);

            CompletableFuture.allOf(fails, task).join();

            sysService.sendMessage(String.format(message,
                    LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                    CollectionUtil.isEmpty(ids) ? 0 : ids.size(),
                    fails.join().size(),
                    String.join("\n- ", fails.join()),
                    task.join()
            ));
        }, new CronTrigger(null == dbc ? CacheConstant.TASK_CRON : dbc.getValue()));

        TASK_MAP.put(CacheConstant.DAILY_BROADCAST_TASK_ID, scheduled);
    }

    private void supportOciUnknownRegionTask() {
        virtualExecutor.execute(() -> {
            Arrays.stream(OciUnSupportRegionEnum.values()).parallel()
                    .forEach(x -> {
                        try {
                            Region.fromRegionId(x.getRegionId());
                        } catch (Exception exception) {
                            Region.register(x.getRegionId(), x.getRealm(), x.getRegionCode());
                            log.info("support new region: [{}] successfully", x.getRegionId());
                        }
                    });
        });
    }

    private void initMapData() {
        virtualExecutor.execute(() -> {
            try {
                log.info("正在初始化地图数据，调用 ip-api.com API...");
                String jsonStr = HttpUtil.get("http://ip-api.com/json/?fields=status,message,country,regionName,city,lat,lon,isp,as,query");
                
                // 验证返回内容是否为有效JSON
                if (jsonStr == null || jsonStr.trim().isEmpty()) {
                    log.warn("ip-api.com API 返回空内容，跳过地图数据初始化");
                    return;
                }
                
                // 检查是否返回HTML而不是JSON
                if (jsonStr.trim().startsWith("<")) {
                    log.warn("ip-api.com API 返回HTML而非JSON，可能服务异常。返回内容前100字符：{}", 
                            jsonStr.substring(0, Math.min(100, jsonStr.length())));
                    return;
                }
                
                JSONObject json = JSONUtil.parseObj(jsonStr);
                
                // 检查API返回状态
                if (!"success".equals(json.getStr("status"))) {
                    log.warn("ip-api.com API 返回失败：{}", json.getStr("message"));
                    return;
                }
                
                // 验证必要字段
                if (!json.containsKey("query") || !json.containsKey("lat") || !json.containsKey("lon")) {
                    log.warn("ip-api.com API 返回的JSON缺少必要字段。返回内容：{}", jsonStr);
                    return;
                }
                
                IpData ipData = new IpData();
                ipData.setId(IdUtil.getSnowflakeNextIdStr());
                ipData.setIp(json.getStr("query"));  // ip-api.com uses "query" for IP
                ipData.setCountry(json.getStr("country"));
                ipData.setArea(json.getStr("regionName"));  // ip-api.com uses "regionName"
                ipData.setCity(json.getStr("city"));
                ipData.setOrg(json.getStr("isp"));  // ip-api.com uses "isp" for organization
                ipData.setAsn(json.getStr("as"));  // ip-api.com uses "as" for ASN
                ipData.setLat(json.getDouble("lat"));  // ip-api.com returns numbers, not strings
                ipData.setLng(json.getDouble("lon"));  // ip-api.com uses "lon" instead of "lng"
                
                List<IpData> ipDataList = ipDataService.list(new LambdaQueryWrapper<IpData>()
                        .eq(IpData::getIp, json.getStr("query")));
                if (CollectionUtil.isNotEmpty(ipDataList)) {
                    ipDataService.remove(new LambdaQueryWrapper<IpData>().eq(IpData::getIp, json.getStr("query")));
                }
                ipDataService.save(ipData);
                log.info("✅ 新增地图IP数据：{} ({}, {}) 成功", ipData.getIp(), ipData.getCity(), ipData.getCountry());
            } catch (Exception e) {
                log.error("初始化地图数据失败，跳过该步骤。错误详情：{}", e.getMessage(), e);
                // 不抛出异常，避免影响其他启动任务
            }
        });
    }
}
