package com.pkfare.trip.scale.plan.service.param;

import com.fasterxml.jackson.databind.JsonNode;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import javax.validation.Valid;
import lombok.Data;

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
    private JsonNode adjustPlanParams;
}