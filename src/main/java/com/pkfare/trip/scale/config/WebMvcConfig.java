package com.pkfare.trip.scale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 * 配置异步请求超时等设置
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${spring.mvc.async.request-timeout:120000}")
    private long asyncRequestTimeout;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        log.info("Configuring async request timeout: {} ms", asyncRequestTimeout);
        configurer.setDefaultTimeout(asyncRequestTimeout);
        
        // 设置异步请求超时处理器
        configurer.registerCallableInterceptors(new AsyncTimeoutCallableProcessingInterceptor());
        configurer.registerDeferredResultInterceptors(new AsyncTimeoutDeferredResultProcessingInterceptor());
    }
}
