package com.pkfare.trip.scale.model.enums;

/**
 * 交通工具类型枚举
 * 
 * @author Trip Scale Team
 */
public enum TransportationType {
    FLIGHT("flight"),
    TRAIN("train"),
    BUS("bus"),
    CAR("car"),
    WALK("walk");
    
    private final String description;
    
    TransportationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
