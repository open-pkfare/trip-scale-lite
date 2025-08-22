package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 活动搜索结果DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class ActivitySearchResult {
    
    /**
     * 城市代码与活动列表的映射
     * key: cityCode, value: List<ActivityInfo>
     */
    private Map<String, List<ActivityInfo>> cityActivityMap;
}
