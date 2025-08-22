package com.pkfare.trip.scale.plan.service.param;

import com.pkfare.trip.scale.plan.service.response.TripPlan;
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
    private TripPlan tripPlan;

    /**
     * 调整参数列表
     */
    @Valid
    private List<AdjustPlanParam> adjustPlanParams;
}