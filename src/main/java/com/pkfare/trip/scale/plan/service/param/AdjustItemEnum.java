package com.pkfare.trip.scale.plan.service.param;

import java.util.Optional;
import lombok.Getter;

/**
 * 调整项类型枚举
 */
public enum AdjustItemEnum {
    FLIGHT("flight", "航班"),
    HOTEL("hotel", "酒店"),
    ACTIVITY("activity", "活动");

    @Getter
    private final String code;
    
    @Getter
    private final String name;

    AdjustItemEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据code获取枚举值
     * @param code 编码
     * @return 枚举值
     */
    public static Optional<AdjustItemEnum> getByCode(String code) {
        for (AdjustItemEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}