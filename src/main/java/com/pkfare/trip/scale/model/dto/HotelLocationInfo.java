package com.pkfare.trip.scale.model.dto;

import lombok.Data;

/**
 * 酒店位置信息DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class HotelLocationInfo {
    
    /**
     * 纬度
     */
    private double latitude;
    
    /**
     * 经度
     */
    private double longitude;
    
    public HotelLocationInfo(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
