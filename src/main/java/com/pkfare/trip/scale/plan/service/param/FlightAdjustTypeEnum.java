package com.pkfare.trip.scale.plan.service.param;

import java.util.Optional;
import lombok.Getter;

/**
 * 航班调整类型枚举
 */
public enum FlightAdjustTypeEnum {
    REPLACE("replace", "替换"),
    ADVANCE("advance", "提前"),
    DELAY("delay", "推迟"),
    CHEAPER("cheaper", "更便宜的");

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
    public static Optional<FlightAdjustTypeEnum> getByCode(String code) {
        for (FlightAdjustTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}