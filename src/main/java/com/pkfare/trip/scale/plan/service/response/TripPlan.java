package com.pkfare.trip.scale.plan.service.response;

import com.pkfare.trip.scale.model.enums.PlanStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行计划响应实体
 * 
 * @author Trip Scale Team
 */
@Data
public class TripPlan {
    
    /**
     * 计划ID，UUID生成
     */
    private String planId;
    
    /**
     * 总费用
     */
    private BigDecimal totalCost;
    
    /**
     * 币种
     */
    private String currency;
    
    /**
     * 计划状态：SUCCESS/OVER_BUDGET/NO_AVAILABLE_OPTION
     */
    private PlanStatus status;
    
    /**
     * 航班信息列表
     */
    private List<FlightInfo> flights;
    
    /**
     * 酒店信息列表
     */
    private List<HotelInfo> hotels;
    
    /**
     * 活动信息列表
     */
    private List<ActivityInfo> activities;
    
    /**
     * 每日行程安排
     */
    private List<DailySchedule> dailySchedules;
    
    /**
     * AI生成的计划文本
     */
    private String aiGeneratedPlan;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 错误信息（状态非SUCCESS时）
     */
    private String errorMessage;
}
