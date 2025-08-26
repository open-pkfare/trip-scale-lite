package com.pkfare.trip.scale.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.InMemorySessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 父子容器配置
 * 为 AdkWebServer 子容器提供必要的 Bean
 */
@Configuration
@Slf4j
public class ParentChildContextConfig {

    /**
     * 提供 loadedAgentRegistry Bean
     * 只在父容器中创建，子容器可以继承
     */
    @Bean
    public Map<String, BaseAgent> loadedAgentRegistry() {
        log.info("Creating loadedAgentRegistry bean for parent-child context");
        return new ConcurrentHashMap<>();
    }

    /**
     * 提供 BaseSessionService Bean
     * 只在父容器中创建，子容器可以继承
     */
    @Bean
    public BaseSessionService sessionService() {
        log.info("Creating BaseSessionService bean for parent-child context");
        return new InMemorySessionService();
    }
}
