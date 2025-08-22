package com.pkfare.trip.scale.plan.service.param;

import lombok.Getter;

/**
 * 航班调整类型枚举
 */
public enum FlightAdjustTypeEnum {
    REPLACE("replace", "替换航班"),
    ADVANCE("advance", "提前航班"),
    DELAY("delay", "推迟航班"),
    CHEAPER("cheaper", "更便宜的航班"),
    CHANGE_DEPARTURE_AIRPORT("changeDepartureAirport", "更改出发机场"),
    CHANGE_ARRIVAL_AIRPORT("changeArriveAirport", "更改到达机场");

    @Getter
    private final String code;
    
    @Getter
    private final String name;

    FlightAdjustTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据code获取枚举值
     * @param code 编码
     * @return 枚举值
     */
    public static FlightAdjustTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (FlightAdjustTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}