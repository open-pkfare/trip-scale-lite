package com.pkfare.trip.scale;

import com.google.adk.web.AdkWebServer;
import com.pkfare.trip.scale.agent.optimizing.OptimizingAgent;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@ComponentScan(basePackages = {"com.pkfare.trip.scale", "com.google.adk.web.config"})
@Slf4j
public class TripScaleApplication {
    public static void main(String[] args) {
        String serverType = System.getProperty("serverType","server");
        ApplicationContext context = null;
        
        switch (serverType){
            case "dev":
                System.setProperty("org.apache.tomcat.websocket.DEFAULT_BUFFER_SIZE", String.valueOf(10485760));
                context = createParentChildContext(args);
                break;
            default:
                context = SpringApplication.run(TripScaleApplication.class, args);
                break;
        }

        PlanningAgent.setApplicationContext(context);
        OptimizingAgent.setApplicationContext(context);
        AnotherRootAgent.setApplicationContext(context);
    }
    
    /**
     * 创建父子容器
     * 父容器：TripScaleApplication（包含所有业务组件）
     * 子容器：AdkWebServer（ADK 相关组件）
     */
    private static ApplicationContext createParentChildContext(String[] args) {
        log.info("Creating parent-child ApplicationContext for dev mode");
        
        // 1. 创建父容器 - TripScaleApplication
        SpringApplication parentApp = new SpringApplication(TripScaleApplication.class);
        parentApp.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE); // 父容器不启动 Web 服务
        org.springframework.context.ConfigurableApplicationContext parentContext = parentApp.run(args);
        log.info("Parent context created with {} beans", parentContext.getBeanDefinitionCount());
        
        // 2. 创建子容器 - AdkWebServer，并设置父容器
        // 通过 SpringApplicationBuilder 设置父容器
        org.springframework.boot.builder.SpringApplicationBuilder builder = 
            new org.springframework.boot.builder.SpringApplicationBuilder(AdkWebServer.class)
                .parent(parentContext);
        
        ApplicationContext childContext = builder.run(args);
        log.info("Child context created with {} beans", childContext.getBeanDefinitionCount());
        
        // 3. 返回子容器（子容器可以访问父容器的 Bean）
        return childContext;
    }
}