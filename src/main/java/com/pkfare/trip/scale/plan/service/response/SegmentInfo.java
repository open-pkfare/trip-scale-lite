package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

/**
 * 航段信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class SegmentInfo {
    /**
     * 航段ID，格式可以为出发-达到机场三字码，例如："SIN-DEL"
     */
    private String id;
    /**
     * 出发地址
     */
    private String departure;
    
    /**
     * 出发时间
     */
    private String departureTime;

    private GeoInfo departureGeo;
    
    /**
     * 到达地址
     */
    private String arrival;
    
    /**
     * 到达时间
     */
    private String arrivalTime;

    private GeoInfo arrivalGeo;
    
    /**
     * 航司
     */
    private String carrierCode;
    
    /**
     * 航班号
     */
    private String number;
}
