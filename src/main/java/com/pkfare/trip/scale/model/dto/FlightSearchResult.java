package com.pkfare.trip.scale.model.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 航班搜索结果DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class FlightSearchResult {
    
    /**
     * 出发日期
     */
    private LocalDate departureDate;
    
    /**
     * 返程日期
     */
    private LocalDate returnDate;
    
    /**
     * 是否精确时间
     */
    private boolean preciseTravel;
    
    /**
     * 是否往返行程
     */
    private boolean roundTrip;
}
