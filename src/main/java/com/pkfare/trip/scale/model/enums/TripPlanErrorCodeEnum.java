package com.pkfare.trip.scale.model.enums;

import lombok.Getter;

/**
 * 旅行计划错误码枚举
 */
public enum TripPlanErrorCodeEnum {
    /**
     * 没有找到合适的航班
     */
    NO_FLIGHT_FOUND("TP001", "没有找到合适的航班"),
    
    /**
     * 没有找到合适的酒店
     */
    NO_HOTEL_FOUND("TP002", "没有找到合适的酒店"),
    
    /**
     * 没有找到合适的活动
     */
    NO_ACTIVITY_FOUND("TP003", "没有找到合适的活动"),
    
    /**
     * 参数错误
     */
    PARAM_ERROR("TP004", "参数错误"),
    
    /**
     * 计划不存在
     */
    PLAN_NOT_EXIST("TP005", "计划不存在"),
    
    /**
     * 服务器内部错误
     */
    SERVER_ERROR("TP999", "服务器内部错误");

    @Getter
    private final String code;
    
    @Getter
    private final String message;

    TripPlanErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据错误码获取枚举值
     * @param code 错误码
     * @return 枚举值
     */
    public static TripPlanErrorCodeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (TripPlanErrorCodeEnum errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return null;
    }
}