package com.pkfare.trip.scale.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 酒店搜索结果DTO
 * 
 * @author Trip Scale Team
 */
@Data
public class HotelSearchResult {
    
    /**
     * 城市代码与酒店ID列表的映射
     * key: location_code, value: List<hotelId>
     */
    private Map<String, List<String>> localHotelIdMap;
}
