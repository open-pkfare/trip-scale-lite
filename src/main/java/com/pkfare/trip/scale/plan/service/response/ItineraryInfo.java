package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.util.List;

/**
 * 行程信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class ItineraryInfo {
    
    /**
     * 航段列表
     */
    private List<SegmentInfo> segments;
}
