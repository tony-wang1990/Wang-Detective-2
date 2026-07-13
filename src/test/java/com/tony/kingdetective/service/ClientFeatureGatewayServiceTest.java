package com.tony.kingdetective.service;

import com.tony.kingdetective.telegram.factory.CallbackHandlerFactory;
import com.tony.kingdetective.telegram.handler.CallbackHandler;
import com.tony.kingdetective.telegram.service.TgSessionFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientFeatureGatewayServiceTest {

    @Mock
    private CallbackHandlerFactory handlerFactory;
    @Mock
    private IAuditLogService auditLogService;
    @Mock
    private TgSessionFlowService sessionFlowService;

    private ClientFeatureGatewayService service;

    @BeforeEach
    void setUp() {
        when(handlerFactory.getRegisteredPatterns()).thenReturn(List.of("sample", "cancel"));
        service = new ClientFeatureGatewayService(
                handlerFactory,
                new OperationAuditSupport(auditLogService),
                sessionFlowService
        );
    }

    @Test
    void menuExposesClientButtonsAndHandlerCoverage() {
        ClientFeatureGatewayService.GatewayResponse response = service.menu();

        assertThat(response.registeredHandlerCount()).isEqualTo(2);
        assertThat(response.buttons()).isNotEmpty();
        assertThat(response.buttons().stream()
                .flatMap(List::stream)
                .map(ClientFeatureGatewayService.GatewayButton::callbackData))
                .contains("quota_query", "cost_query", "instance_monitoring");
    }

    @Test
    void callbackNormalizesTextAndInlineButtons() {
        CallbackHandler handler = new CallbackHandler() {
            @Override
            public BotApiMethod<? extends Serializable> handle(CallbackQuery query, TelegramClient client) {
                return EditMessageText.builder()
                        .chatId(query.getMessage().getChatId())
                        .messageId(query.getMessage().getMessageId())
                        .text("quota result")
                        .parseMode("MarkdownV2")
                        .replyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(
                                InlineKeyboardButton.builder().text("back").callbackData("back_to_main").build()
                        ))))
                        .build();
            }

            @Override
            public String getCallbackPattern() {
                return "sample";
            }
        };
        when(handlerFactory.getHandler("sample:quota")).thenReturn(Optional.of(handler));

        ClientFeatureGatewayService.GatewayResponse response = service.invoke("sample:quota", "test-session");

        assertThat(response.text()).isEqualTo("quota result");
        assertThat(response.parseMode()).isEqualTo("MarkdownV2");
        assertThat(response.buttons()).hasSize(1);
        assertThat(response.buttons().getFirst().getFirst().callbackData()).isEqualTo("back_to_main");
    }

    @Test
    void callbackCopiesTelegramDocumentIntoAuthenticatedAttachmentStore() throws Exception {
        Path source = Files.createTempFile("gateway-test-", ".txt");
        Files.writeString(source, "client attachment");
        CallbackHandler handler = new CallbackHandler() {
            @Override
            public BotApiMethod<? extends Serializable> handle(CallbackQuery query, TelegramClient client) {
                try {
                    client.execute(SendDocument.builder()
                            .chatId(query.getMessage().getChatId())
                            .document(new InputFile(source.toFile()))
                            .build());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return EditMessageText.builder()
                        .chatId(query.getMessage().getChatId())
                        .messageId(query.getMessage().getMessageId())
                        .text("done")
                        .build();
            }

            @Override
            public String getCallbackPattern() {
                return "sample";
            }
        };
        when(handlerFactory.getHandler(anyString())).thenReturn(Optional.of(handler));

        ClientFeatureGatewayService.GatewayResponse response = service.invoke("sample:file", "attachment-session");
        Files.deleteIfExists(source);

        assertThat(response.attachments()).hasSize(1);
        ClientFeatureGatewayService.AttachmentView view = response.attachments().getFirst();
        ClientFeatureGatewayService.AttachmentDownload download = service.getAttachment(view.token()).orElseThrow();
        assertThat(Files.readString(download.path())).isEqualTo("client attachment");
        Files.deleteIfExists(download.path());
    }

    @Test
    void callbackDeclaresNativeInputAndClientRouteForSessionFlows() {
        CallbackHandler handler = new CallbackHandler() {
            @Override
            public BotApiMethod<? extends Serializable> handle(CallbackQuery query, TelegramClient client) {
                return EditMessageText.builder()
                        .chatId(query.getMessage().getChatId())
                        .messageId(query.getMessage().getMessageId())
                        .text("continue")
                        .build();
            }

            @Override
            public String getCallbackPattern() {
                return "session";
            }
        };
        when(handlerFactory.getHandler(anyString())).thenReturn(Optional.of(handler));

        ClientFeatureGatewayService.GatewayResponse backup = service.invoke(
                "backup_execute_encrypted",
                "backup-session"
        );
        ClientFeatureGatewayService.GatewayResponse restore = service.invoke("restore_start", "restore-session");
        ClientFeatureGatewayService.GatewayResponse account = service.invoke("account_add_bot", "account-session");

        assertThat(backup.inputPrompt().type()).isEqualTo("password");
        assertThat(backup.inputPrompt().minLength()).isEqualTo(8);
        assertThat(restore.inputPrompt().type()).isEqualTo("file");
        assertThat(restore.inputPrompt().confirmText()).isNotBlank();
        assertThat(account.clientRoute()).isEqualTo("/dashboard/user");
    }
}
