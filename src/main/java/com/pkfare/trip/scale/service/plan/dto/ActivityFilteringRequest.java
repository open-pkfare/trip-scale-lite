package com.pkfare.trip.scale.service.plan.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI活动筛选请求DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class ActivityFilteringRequest {
    
    /**
     * 城市代码
     */
    private String cityCode;
    
    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 筛选日期
     */
    private LocalDate date;
    
    /**
     * 航班信息
     */
    private FlightTimeInfo flightInfo;
    
    /**
     * 用户偏好
     */
    private UserPreferences userPreferences;
    
    /**
     * 候选活动列表
     */
    private List<ActivityInfo> candidateActivities;
    
    /**
     * 预算信息
     */
    private String budget;
    
    /**
     * 货币
     */
    private String currency;
    
    /**
     * 额外上下文信息
     */
    private Map<String, Object> context;
    
    /**
     * 航班时间信息
     */
    @Data
    public static class FlightTimeInfo {
        private String type; // arrival_day, departure_day, full_day
        private String arrivalTime;
        private String departureTime;
        private LocalDate date;
    }
    
    /**
     * 用户偏好信息
     */
    @Data
    public static class UserPreferences {
        private List<String> likes;
        private List<String> hates;
        private List<String> prefer;
        private Map<String, Object> additionalPreferences;
    }
}
