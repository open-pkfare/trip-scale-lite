package com.pkfare.trip.scale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Amadeus API配置类
 * 
 * @author Trip Scale Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "amadeus")
public class AmadeusConfig {
    
    /**
     * 客户端ID
     */
    private String clientId;
    
    /**
     * 客户端密钥
     */
    private String clientSecret;
    
    /**
     * API基础URL
     */
    private String baseUrl = "https://test.api.amadeus.com/v1";
    
    /**
     * 超时时间（毫秒）
     */
    private int timeoutMs = 30000;
    
    /**
     * 最大连接数
     */
    private int maxConnections = 100;
}
