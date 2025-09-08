package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 酒店信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class HotelInfo {
    
    /**
     * 酒店ID
     */
    private String hotelId;
    
    /**
     * 重复ID
     */
    private String dupeId;
    
    /**
     * 报价ID
     */
    private String offerId;
    
    /**
     * 酒店名称
     */
    private String hotelName;
    
    /**
     * 城市代码
     */
    private String cityCode;

    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 入住日期
     */
    private LocalDate checkInDate;
    
    /**
     * 退房日期
     */
    private LocalDate checkOutDate;
    
    /**
     * 住宿夜数
     */
    private int nights;
    
    /**
     * 总价格
     */
    private BigDecimal totalPrice;
    
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
     * 地址
     */
    private String address;
    


    /**
     * 是否首选
     */
    private Boolean preferred = false;

    private RoomDetails roomDetails;
}
