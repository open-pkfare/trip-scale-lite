package com.pkfare.trip.scale.plan.service.param;

import com.google.gson.JsonArray;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import lombok.Data;

import javax.validation.Valid;
import java.util.List;

/**
 * 调整旅行计划请求包装类
 * 
 * @author Trip Scale Team
 */
@Data
public class AdjustPlanRequest {
    /**
     * 生成计划参数
     */
    @Valid
    private GeneratePlanParam generatePlanParam;

    /**
     * 原始旅行计划
     */
    @Valid
    private TripRoutePlanResult tripPlan;

    /**
     * 调整参数列表
     */
    @Valid
    private JsonArray adjustPlanParams;
}