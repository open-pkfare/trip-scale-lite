package com.pkfare.trip.scale.model.enums;

/**
 * 旅行计划状态枚举
 * 
 * @author Trip Scale Team
 */
public enum PlanStatus {
    SUCCESS("generated successfully"),
    OVER_BUDGET("over budget"),
    NO_AVAILABLE_OPTION("no available options"),
    API_ERROR("API call failed"),
    PARAM_ERROR("parameter error");
    
    private final String description;
    
    PlanStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
