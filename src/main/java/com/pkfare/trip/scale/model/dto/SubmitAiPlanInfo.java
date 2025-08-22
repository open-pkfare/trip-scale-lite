package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import lombok.Data;
import java.util.List;

/**
 * 提交AI计划生成的信息DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class SubmitAiPlanInfo {
    
    /**
     * 生成计划参数
     */
    private GeneratePlanParam generatePlanParam;
    
    /**
     * 航班信息
     */
    private List<FlightInfo> flightInfos;
    
    /**
     * 酒店信息列表
     */
    private List<HotelInfo> hotelInfos;
    
    /**
     * 活动信息列表
     */
    private List<ActivityInfo> activityInfos;
}
