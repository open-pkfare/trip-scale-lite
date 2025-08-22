package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日行程安排实体
 * 
 * @author Trip Scale Team
 */
@Data
public class DailySchedule {
    
    /**
     * 日期
     */
    private LocalDate date;
    
    /**
     * 城市代码
     */
    private String cityCode;
    
    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 当日酒店
     */
    private HotelInfo hotel;
    
    /**
     * 当日活动列表
     */
    private List<ActivityInfo> activities;
    
    /**
     * 交通信息（城市间移动）
     */
    private TransportationInfo transportation;
    
    /**
     * 备注信息
     */
    private String notes;
    
    /**
     * 当日费用
     */
    private BigDecimal dailyCost;
}
