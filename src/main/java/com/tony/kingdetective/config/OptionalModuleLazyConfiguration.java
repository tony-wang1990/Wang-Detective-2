package com.tony.kingdetective.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Keeps optional Telegram and AI modules out of the critical web startup path.
 */
@Configuration(proxyBeanMethods = false)
public class OptionalModuleLazyConfiguration {

    private static final List<String> LAZY_CLASS_PREFIXES = List.of(
            "com.tony.kingdetective.telegram.handler.",
            "com.tony.kingdetective.telegram.factory.CallbackHandlerFactory",
            "com.tony.kingdetective.telegram.service.AiChatService",
            "com.tony.kingdetective.controller.AiChatController",
            "com.tony.kingdetective.config.ai.DynamicChatClientFactory",
            "com.tony.kingdetective.utils.search.DuckDuckGoSearchService"
    );

    @Bean
    static BeanFactoryPostProcessor optionalModuleLazyInitializer() {
        return beanFactory -> {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
                String className = definition.getBeanClassName();
                if (className != null && LAZY_CLASS_PREFIXES.stream().anyMatch(className::startsWith)) {
                    definition.setLazyInit(true);
                }
            }
        };
    }
}
