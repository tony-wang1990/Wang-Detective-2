package com.tony.kingdetective.config.auth;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author: Tony Wang
 * @date: 2024/3/30 15:28
 */
@Configuration
public class CrossDomainFilter implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Value("${web.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${clients.download-dir:/app/king-detective/deploy/downloads}")
    private String clientDownloadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
                .exposedHeaders("Upgrade", "Connection", "Content-Disposition");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations(Paths.get(clientDownloadDir)
                        .toAbsolutePath()
                        .normalize()
                        .toUri()
                        .toString());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/dist/");
    }
}
