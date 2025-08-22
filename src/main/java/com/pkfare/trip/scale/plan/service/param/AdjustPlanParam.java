package com.pkfare.trip.scale.plan.service.param;

import lombok.Data;

/**
 * 调整旅行计划参数
 * 
 * @author Trip Scale Team
 */
@Data
public class AdjustPlanParam {
    /**
     * 调整项类型: flight, hotel, activity
     */
    private String item;

    /**
     * 调整项ID
     */
    private String id;

    /**
     * 调整类型: replace, advance, delay, cheaper, changeDepartureAirport
     */
    private String adjustType;

    /**
     * 航空公司
     */
    private String noStop;

    /**
     * 新的出发机场
     */
    private String newDepartureAirport;

    /**
     * 时间变更（小时）
     */
    private Integer timeChange;

    /**
     * 最高价格
     */
    private Double maxPrice;

    /**
     * 偏好
     */
    private String preference;
}