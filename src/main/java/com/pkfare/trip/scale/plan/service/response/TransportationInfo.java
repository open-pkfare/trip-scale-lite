package com.pkfare.trip.scale.plan.service.response;

import com.pkfare.trip.scale.model.enums.TransportationType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交通信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class TransportationInfo {
    
    /**
     * 交通类型：FLIGHT/TRAIN/BUS/CAR
     */
    private TransportationType type;
    
    /**
     * 出发地
     */
    private String from;
    
    /**
     * 目的地
     */
    private String to;
    
    /**
     * 行程时间
     */
    private String duration;
    
    /**
     * 交通费用
     */
    private BigDecimal cost;
    
    /**
     * 描述信息
     */
    private String description;
    
    /**
     * 出发时间
     */
    private LocalDateTime departureTime;
    
    /**
     * 到达时间
     */
    private LocalDateTime arrivalTime;
}
