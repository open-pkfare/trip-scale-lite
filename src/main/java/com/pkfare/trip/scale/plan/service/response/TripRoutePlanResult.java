package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.util.List;

/**
 * 旅行路线规划结果实体
 * 
 * @author Trip Scale Team
 */
@Data
public class TripRoutePlanResult {
    
    /**
     * 规划状态
     */
    private String status;
    
    /**
     * 每日路线规划列表
     */
    private List<DailyRoutePlan> dailyPlans;
    
    /**
     * 总距离（米）
     */
    private Long totalDistance;
    
    /**
     * 总时间（秒）
     */
    private Long totalDuration;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 规划摘要
     */
    private String summary;
}
