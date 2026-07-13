package com.tony.kingdetective.service;

import com.tony.kingdetective.telegram.builder.KeyboardBuilder;
import com.tony.kingdetective.telegram.factory.CallbackHandlerFactory;
import com.tony.kingdetective.telegram.handler.CallbackHandler;
import com.tony.kingdetective.telegram.service.TgSessionFlowService;
import com.tony.kingdetective.telegram.storage.ConfigSessionStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientFeatureGatewayService {

    private static final long ATTACHMENT_TTL_MILLIS = 15 * 60 * 1000L;
    private static final long MAX_RESTORE_FILE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_CALLBACK_LENGTH = 1024;

    private final CallbackHandlerFactory handlerFactory;
    private final OperationAuditSupport audit;
    private final TgSessionFlowService sessionFlowService;
    private final Map<String, StoredAttachment> attachments = new ConcurrentHashMap<>();

    public ClientFeatureGatewayService(
            CallbackHandlerFactory handlerFactory,
            OperationAuditSupport audit,
            TgSessionFlowService sessionFlowService
    ) {
        this.handlerFactory = handlerFactory;
        this.audit = audit;
        this.sessionFlowService = sessionFlowService;
    }

    public GatewayResponse menu() {
        cleanupExpiredAttachments();
        return new GatewayResponse(
                "全部功能",
                "请选择需要执行的操作。这里直接复用项目现有的 Telegram 业务处理器，Windows、Android 和 Web 的结果保持一致。",
                "plain",
                toButtonRows(new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())),
                List.of(),
                List.of(),
                null,
                null,
                false,
                handlerFactory.getRegisteredPatterns().size()
        );
    }

    public GatewayResponse invoke(String callbackData, String sessionId) {
        validateCallbackData(callbackData);
        String normalizedSessionId = normalizeSessionId(sessionId);
        CallbackHandler handler = handlerFactory.getHandler(callbackData)
                .orElseThrow(() -> new IllegalArgumentException("未找到功能处理器: " + callbackData));

        return audit.supply(
                "CLIENT_FEATURE_CALLBACK",
                handler.getCallbackPattern(),
                "callback=" + callbackData,
                () -> executeHandler(handler, callbackData, normalizedSessionId)
        );
    }

    public List<String> registeredPatterns() {
        return handlerFactory.getRegisteredPatterns();
    }

    public GatewayResponse submitInput(String callbackData, String sessionId, String value) {
        validateCallbackData(callbackData);
        String normalizedSessionId = normalizeSessionId(sessionId);
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.length() > 2048) {
            throw new IllegalArgumentException("输入内容长度超出限制");
        }

        return audit.supply(
                "CLIENT_FEATURE_INPUT",
                callbackData,
                "session=" + normalizedSessionId,
                () -> switch (callbackData) {
                    case "vnc_setup" -> saveVncInput(normalizedSessionId, normalizedValue);
                    case "backup_execute_encrypted" -> createEncryptedBackup(normalizedSessionId, normalizedValue);
                    default -> throw new IllegalArgumentException("该功能不接受文本输入: " + callbackData);
                }
        );
    }

    public GatewayResponse restore(String sessionId, MultipartFile file, String password) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择备份 ZIP 文件");
        }
        if (file.getSize() > MAX_RESTORE_FILE_BYTES) {
            throw new IllegalArgumentException("备份文件不能超过 50 MB");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("backup.zip");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("只支持 ZIP 格式的备份文件");
        }
        String normalizedPassword = password == null ? "" : password;
        if (normalizedPassword.length() > 256) {
            throw new IllegalArgumentException("恢复密码长度超出限制");
        }

        return audit.supply(
                "CLIENT_FEATURE_RESTORE",
                fileName,
                "size=" + file.getSize(),
                () -> restoreUpload(normalizedSessionId, file, normalizedPassword)
        );
    }

    public Optional<AttachmentDownload> getAttachment(String token) {
        cleanupExpiredAttachments();
        StoredAttachment attachment = attachments.get(token);
        if (attachment == null || !Files.isRegularFile(attachment.path())) {
            return Optional.empty();
        }
        return Optional.of(new AttachmentDownload(
                attachment.path(),
                attachment.fileName(),
                attachment.contentType()
        ));
    }

    private GatewayResponse executeHandler(CallbackHandler handler, String callbackData, String sessionId) {
        cleanupExpiredAttachments();
        long chatId = sessionChatId(sessionId);
        CallbackQuery callbackQuery = createCallbackQuery(callbackData, chatId);
        CaptureContext capture = new CaptureContext();
        TelegramClient telegramClient = createCaptureClient(capture);

        BotApiMethod<? extends Serializable> result;
        try {
            result = handler.handle(callbackQuery, telegramClient);
        } catch (RuntimeException e) {
            throw new IllegalStateException("功能执行失败: " + rootMessage(e), e);
        }

        if (result != null) {
            capture.capture(result);
        }
        if ("cancel".equals(callbackData)) {
            clearClientSession(sessionId);
            return menu();
        }
        if (clientRoute(callbackData) != null) {
            clearClientSession(sessionId);
        }
        return normalizeResponse(handler, callbackData, capture);
    }

    private GatewayResponse normalizeResponse(CallbackHandler handler, String callbackData, CaptureContext capture) {
        String text = "操作已完成。";
        String parseMode = "plain";
        InlineKeyboardMarkup keyboard = null;
        boolean closed = false;

        for (Object method : capture.methods) {
            if (method instanceof EditMessageText edit) {
                text = edit.getText();
                parseMode = normalizeParseMode(edit.getParseMode());
                keyboard = edit.getReplyMarkup();
                closed = false;
            } else if (method instanceof SendMessage send) {
                text = send.getText();
                parseMode = normalizeParseMode(send.getParseMode());
                keyboard = send.getReplyMarkup() instanceof InlineKeyboardMarkup inline ? inline : null;
                closed = false;
            } else if (method instanceof SendDocument document && document.getCaption() != null) {
                text = document.getCaption();
                parseMode = normalizeParseMode(document.getParseMode());
                keyboard = document.getReplyMarkup() instanceof InlineKeyboardMarkup inline ? inline : keyboard;
            } else if (method instanceof SendPhoto photo && photo.getCaption() != null) {
                text = photo.getCaption();
                parseMode = normalizeParseMode(photo.getParseMode());
                keyboard = photo.getReplyMarkup() instanceof InlineKeyboardMarkup inline ? inline : keyboard;
            } else if (method instanceof DeleteMessage) {
                closed = true;
            }
        }

        return new GatewayResponse(
                handler.getCallbackPattern(),
                text,
                parseMode,
                toButtonRows(keyboard),
                List.copyOf(capture.notices),
                List.copyOf(capture.attachmentViews),
                inputPrompt(callbackData),
                clientRoute(callbackData),
                closed,
                handlerFactory.getRegisteredPatterns().size()
        );
    }

    private GatewayResponse saveVncInput(String sessionId, String value) {
        if (!sessionFlowService.isValidVncUrl(value)) {
            throw new IllegalArgumentException("VNC URL 必须以 http:// 或 https:// 开头");
        }
        String normalizedUrl = sessionFlowService.normalizeVncUrl(value);
        String error = sessionFlowService.saveVncUrl(normalizedUrl);
        if (error != null) {
            throw new IllegalStateException("保存 VNC URL 失败: " + error);
        }
        clearClientSession(sessionId);
        return actionResponse(
                "vnc_setup",
                "VNC URL 配置成功\n\n当前地址：" + normalizedUrl,
                List.of(),
                "vnc_config"
        );
    }

    private GatewayResponse createEncryptedBackup(String sessionId, String password) {
        if (!sessionFlowService.isValidBackupPassword(password)) {
            throw new IllegalArgumentException("备份密码至少需要 8 位字符");
        }
        String sourcePath = null;
        try {
            sourcePath = sessionFlowService.createEncryptedBackup(password);
            Path source = Path.of(sourcePath);
            AttachmentView attachment = storeAttachment(
                    new InputFile(source.toFile()),
                    source.getFileName().toString(),
                    "application/zip"
            );
            if (attachment == null) {
                throw new IllegalStateException("备份文件生成后无法加入下载列表");
            }
            clearClientSession(sessionId);
            return actionResponse(
                    "backup_execute_encrypted",
                    "加密备份已创建。请在 15 分钟内保存下方文件，并妥善保管恢复密码。",
                    List.of(attachment),
                    "backup_restore"
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("加密备份创建失败: " + rootMessage(e), e);
        } finally {
            if (sourcePath != null) {
                sessionFlowService.deleteBackupFile(sourcePath);
            }
        }
    }

    private GatewayResponse restoreUpload(String sessionId, MultipartFile file, String password) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("wang-detective-restore-", ".zip");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            sessionFlowService.restoreFromBackup(tempFile.toString(), password);
            clearClientSession(sessionId);
            return actionResponse(
                    "restore_start",
                    "数据恢复成功。数据库、密钥和任务配置已经载入，请按运行环境需要重启服务。",
                    List.of(),
                    "backup_restore"
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("恢复数据失败: " + rootMessage(e), e);
        } finally {
            if (tempFile != null) {
                sessionFlowService.deleteBackupFile(tempFile.toString());
            }
        }
    }

    private GatewayResponse actionResponse(
            String title,
            String text,
            List<AttachmentView> attachmentViews,
            String backCallback
    ) {
        List<List<GatewayButton>> rows = List.of(
                List.of(new GatewayButton("返回", backCallback, null)),
                List.of(new GatewayButton("主菜单", "back_to_main", null))
        );
        return new GatewayResponse(
                title,
                text,
                "plain",
                rows,
                List.of(),
                attachmentViews,
                null,
                null,
                false,
                handlerFactory.getRegisteredPatterns().size()
        );
    }

    private InputPrompt inputPrompt(String callbackData) {
        return switch (callbackData) {
            case "vnc_setup" -> new InputPrompt(
                    "vnc_setup", "url", "VNC URL", "https://vnc.example.com", null, 8, null
            );
            case "backup_execute_encrypted" -> new InputPrompt(
                    "backup_execute_encrypted", "password", "备份加密密码", "至少 8 位字符", null, 8, null
            );
            case "restore_start" -> new InputPrompt(
                    "restore_start", "file", "备份 ZIP 文件", "可选：输入加密备份密码", ".zip", 0,
                    "我确认恢复操作将覆盖当前数据"
            );
            default -> null;
        };
    }

    private String clientRoute(String callbackData) {
        return "account_add_bot".equals(callbackData) ? "/dashboard/user" : null;
    }

    private void clearClientSession(String sessionId) {
        ConfigSessionStorage.getInstance().clearSession(sessionChatId(sessionId));
    }

    private TelegramClient createCaptureClient(CaptureContext capture) {
        return (TelegramClient) Proxy.newProxyInstance(
                TelegramClient.class.getClassLoader(),
                new Class<?>[]{TelegramClient.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ClientFeatureCaptureTelegramClient";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Object apiMethod = args != null && args.length > 0 ? args[0] : null;
                    if (method.getName().startsWith("execute") && apiMethod != null) {
                        capture.capture(apiMethod);
                    }
                    Object result = defaultExecutionResult(apiMethod);
                    if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                        return CompletableFuture.completedFuture(result);
                    }
                    if (method.getReturnType() == Boolean.class || method.getReturnType() == boolean.class) {
                        return Boolean.TRUE;
                    }
                    if (method.getReturnType() == Message.class) {
                        return new Message();
                    }
                    return result;
                }
        );
    }

    private Object defaultExecutionResult(Object apiMethod) {
        if (apiMethod instanceof SendDocument || apiMethod instanceof SendPhoto) {
            return new Message();
        }
        if (apiMethod instanceof BotApiMethod<?>) {
            return Boolean.TRUE;
        }
        return null;
    }

    private CallbackQuery createCallbackQuery(String callbackData, long chatId) {
        Chat chat = new Chat(chatId, "private");
        Message message = new Message();
        message.setMessageId(1);
        message.setDate(Math.toIntExact(Instant.now().getEpochSecond()));
        message.setChat(chat);

        CallbackQuery query = new CallbackQuery();
        query.setId(UUID.randomUUID().toString());
        query.setChatInstance(Long.toString(chatId));
        query.setData(callbackData);
        query.setMessage(message);
        return query;
    }

    private List<List<GatewayButton>> toButtonRows(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return List.of();
        }
        List<List<GatewayButton>> rows = new ArrayList<>();
        for (InlineKeyboardRow row : markup.getKeyboard()) {
            List<GatewayButton> buttons = new ArrayList<>();
            for (InlineKeyboardButton button : row) {
                buttons.add(new GatewayButton(
                        button.getText(),
                        button.getCallbackData(),
                        button.getUrl()
                ));
            }
            if (!buttons.isEmpty()) {
                rows.add(List.copyOf(buttons));
            }
        }
        return List.copyOf(rows);
    }

    private void validateCallbackData(String callbackData) {
        if (callbackData == null || callbackData.isBlank()) {
            throw new IllegalArgumentException("callbackData 不能为空");
        }
        if (callbackData.length() > MAX_CALLBACK_LENGTH) {
            throw new IllegalArgumentException("callbackData 长度超出限制");
        }
        if (callbackData.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("callbackData 含有非法控制字符");
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (sessionId.length() > 128) {
            throw new IllegalArgumentException("sessionId 长度超出限制");
        }
        return sessionId.trim();
    }

    private long sessionChatId(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return value == 0 ? -1 : -value;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String normalizeParseMode(String parseMode) {
        return parseMode == null || parseMode.isBlank() ? "plain" : parseMode;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void cleanupExpiredAttachments() {
        long now = System.currentTimeMillis();
        attachments.entrySet().removeIf(entry -> {
            StoredAttachment value = entry.getValue();
            if (value.expiresAt() > now) {
                return false;
            }
            try {
                Files.deleteIfExists(value.path());
            } catch (IOException ignored) {
                // Expired entries are removed even if the temporary file is already unavailable.
            }
            return true;
        });
    }

    private AttachmentView storeAttachment(InputFile inputFile, String defaultName, String defaultContentType) {
        if (inputFile == null || !inputFile.isNew()) {
            return null;
        }
        String fileName = attachmentFileName(inputFile, defaultName);
        String suffix = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : ".bin";
        try {
            Path tempFile = Files.createTempFile("wang-detective-client-", suffix);
            if (inputFile.getNewMediaFile() != null) {
                Files.copy(inputFile.getNewMediaFile().toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            } else if (inputFile.getNewMediaStream() != null) {
                try (InputStream stream = inputFile.getNewMediaStream()) {
                    Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.deleteIfExists(tempFile);
                return null;
            }

            String contentType = Optional.ofNullable(Files.probeContentType(tempFile)).orElse(defaultContentType);
            String token = UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis() + ATTACHMENT_TTL_MILLIS;
            attachments.put(token, new StoredAttachment(tempFile, fileName, contentType, expiresAt));
            return new AttachmentView(
                    token,
                    fileName,
                    contentType,
                    Files.size(tempFile),
                    "/api/v1/client-features/attachments/" + token,
                    expiresAt
            );
        } catch (IOException e) {
            throw new IllegalStateException("客户端附件暂存失败: " + e.getMessage(), e);
        }
    }

    private String attachmentFileName(InputFile inputFile, String defaultName) {
        String name = inputFile.getNewMediaFile() != null
                ? inputFile.getNewMediaFile().getName()
                : inputFile.getMediaName();
        if (name == null || name.isBlank() || name.startsWith("attach://")) {
            name = defaultName;
        }
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    private final class CaptureContext {
        private final List<Object> methods = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();
        private final List<AttachmentView> attachmentViews = new ArrayList<>();
        private final Set<Object> captured = Collections.newSetFromMap(new IdentityHashMap<>());

        private void capture(Object method) {
            if (method == null || !captured.add(method)) {
                return;
            }
            methods.add(method);
            if (method instanceof AnswerCallbackQuery answer && answer.getText() != null) {
                notices.add(answer.getText());
            } else if (method instanceof SendDocument document) {
                AttachmentView view = storeAttachment(document.getDocument(), "wang-detective-export.bin", "application/octet-stream");
                if (view != null) {
                    attachmentViews.add(view);
                }
            } else if (method instanceof SendPhoto photo) {
                AttachmentView view = storeAttachment(photo.getPhoto(), "wang-detective-image.png", "image/png");
                if (view != null) {
                    attachmentViews.add(view);
                }
            }
        }
    }

    private record StoredAttachment(Path path, String fileName, String contentType, long expiresAt) {
    }

    public record GatewayButton(String text, String callbackData, String url) {
    }

    public record AttachmentView(
            String token,
            String fileName,
            String contentType,
            long size,
            String url,
            long expiresAt
    ) {
    }

    public record InputPrompt(
            String action,
            String type,
            String label,
            String placeholder,
            String accept,
            int minLength,
            String confirmText
    ) {
    }

    public record GatewayResponse(
            String title,
            String text,
            String parseMode,
            List<List<GatewayButton>> buttons,
            List<String> notices,
            List<AttachmentView> attachments,
            InputPrompt inputPrompt,
            String clientRoute,
            boolean closed,
            int registeredHandlerCount
    ) {
    }

    public record AttachmentDownload(Path path, String fileName, String contentType) {
    }
}
