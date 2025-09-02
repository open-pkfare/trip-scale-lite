package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.util.List;

/**
 * 路线段信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class RouteSegment {
    
    /**
     * 起点名称
     */
    private String startName;
    
    /**
     * 起点位置
     */
    private GeoInfo startLocation;
    
    /**
     * 终点名称
     */
    private String endName;
    
    /**
     * 终点位置
     */
    private GeoInfo endLocation;
    
    /**
     * 距离（米）
     */
    private Long distance;
    
    /**
     * 时间（秒）
     */
    private Long duration;
    
    /**
     * 交通方式
     */
    private String travelMode;
    
    /**
     * 路线步骤
     */
    private List<String> steps;
    
    /**
     * 路线概览
     */
    private String overview;

    /**
     * google 返回routes全量信息
     */
    private String routesJson;
}
