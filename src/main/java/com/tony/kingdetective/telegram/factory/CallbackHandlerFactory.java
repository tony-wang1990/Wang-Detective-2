package com.tony.kingdetective.telegram.factory;

import com.tony.kingdetective.telegram.handler.CallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Telegram callback handler registry.
 */
@Slf4j
@Component
public class CallbackHandlerFactory {

    private final List<CallbackHandler> handlers;

    public CallbackHandlerFactory(List<CallbackHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(
                        (CallbackHandler handler) -> handler.getCallbackPattern().length()
                ).reversed())
                .toList();
        log.info("已加载 {} 个 Telegram 回调处理器", handlers.size());
    }

    /**
     * Finds the first handler matching the callback payload.
     */
    public Optional<CallbackHandler> getHandler(String callbackData) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(callbackData))
                .findFirst();
    }

    public List<String> getRegisteredPatterns() {
        return handlers.stream()
                .map(CallbackHandler::getCallbackPattern)
                .toList();
    }
}
