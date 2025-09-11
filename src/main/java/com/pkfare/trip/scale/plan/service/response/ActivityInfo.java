package com.pkfare.trip.scale.plan.service.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import lombok.NoArgsConstructor;

/**
 * 活动信息实体
 * 
 * @author Trip Scale Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityInfo {
    
    /**
     * 活动ID
     */
    private String activityId;
    
    /**
     * 活动名称
     */
    private String name;
    
    /**
     * 活动描述
     */
    private String description;
    
    /**
     * 城市代码
     */
    private String cityCode;

    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 评分
     */
    private double rating;
    
    /**
     * 价格
     */
    private BigDecimal price;
    
    /**
     * 币种
     */
    private String currency;
    
    /**
     * 纬度
     */
    private double latitude;
    
    /**
     * 经度
     */
    private double longitude;
    
    /**
     * 活动类别
     */
    private String type;

    private List<String> pictures;
}
