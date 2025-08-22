package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.util.List;

/**
 * 航班信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class FlightInfo {
    
    /**
     * 是否单程
     */
    private Boolean oneWay;
    
    /**
     * 航班价格
     */
    private String total;
    
    /**
     * 价格币种
     */
    private String currency;
    
    /**
     * 行程集合
     */
    private List<ItineraryInfo> itineraries;
}
