package com.pkfare.trip.scale.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;

/**
 * 旅行计划调整服务接口
 * 
 * @author Trip Scale Team
 */
public interface TripPlanAdjustInterface {
    /**
     * 调整旅行计划
     *
     * @param generatePlanParam 生成计划参数
     * @param tripPlan 原始旅行计划
     * @param adjustParam 调整参数列表
     * @return 调整后的旅行计划
     */
    void adjust(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonNode adjustParam);
}