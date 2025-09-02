package com.pkfare.trip.scale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 旅行计划配置类
 * 
 * @author Trip Scale Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "trip.plan")
public class TripPlanConfig {
    
    /**
     * 最大重试次数
     */
    private int maxRetryAttempts = 3;
    
    /**
     * 重试延迟时间（毫秒）
     */
    private long retryDelayMs = 1000;
    
    /**
     * 每个城市最大活动数量
     */
    private int maxActivitiesPerCity = 5;
    
    /**
     * 活动搜索半径（公里）
     */
    private double activitySearchRadiusKm = 20.0;
    
    /**
     * 活动筛选半径（公里）
     */
    private double activityFilterRadiusKm = 100.0;
    
    /**
     * 默认价格范围
     */
    private String defaultPriceRange = "10,5000";
    
    /**
     * 默认酒店搜索半径
     */
    private int defaultHotelRadius = 20;
    
    /**
     * 默认酒店搜索半径单位
     */
    private String defaultHotelRadiusUnit = "KM";
}
