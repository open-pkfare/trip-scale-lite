package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日路线规划实体
 * 
 * @author Trip Scale Team
 */
@Data
public class DailyRoutePlan {
    
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
     * 首选当日住宿酒店
     */
    private HotelInfo preferredHotel;

    /**
     * 备选当日住宿酒店
     */
    private List<HotelInfo> alternativeHotels;
    
    /**
     * 当日活动列表（按时间顺序）
     */
    private List<ActivityInfo> activities;
    
    /**
     * 路线信息列表
     */
    private List<RouteSegment> routes;
    
    /**
     * 总距离（米）
     */
    private Long totalDistance;
    
    /**
     * 总时间（秒）
     */
    private Long totalDuration;
    
    /**
     * 备注信息
     */
    private String notes;
}
