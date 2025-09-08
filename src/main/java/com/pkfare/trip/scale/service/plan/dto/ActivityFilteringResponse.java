package com.pkfare.trip.scale.service.plan.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Data;

import java.util.List;

/**
 * AI活动筛选响应DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class ActivityFilteringResponse {
    
    /**
     * 筛选状态
     */
    private String status;
    
    /**
     * 推荐的活动列表
     */
    private List<RecommendedActivity> recommendedActivities;
    
    /**
     * 筛选理由
     */
    private String reasoning;
    
    /**
     * 时间建议
     */
    private String timeRecommendation;
    
    /**
     * 注意事项
     */
    private String notes;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 推荐活动信息
     */
    @Data
    public static class RecommendedActivity {
        /**
         * 活动信息
         */
        private ActivityInfo activity;
        
        /**
         * 推荐优先级 (1-5, 5最高)
         */
        private int priority;
        
        /**
         * 推荐理由
         */
        private String reason;
        
        /**
         * 建议游览时间（小时）
         */
        private Double suggestedDuration;
        
        /**
         * 建议开始时间
         */
        private String suggestedStartTime;
        
        /**
         * 是否为必游景点
         */
        private boolean mustVisit;
    }
}
