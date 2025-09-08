package com.pkfare.trip.scale.plan.service.response;

import java.util.List;
import lombok.Data;

/**
 * 酒店信息实体
 * 
 * @author Trip Scale Team
 */
@Data
public class HotelInfo {

    private String type;
    private HotelDetail hotel;
    private boolean available;
    private List<HotelOffer> offers;


    /**
     * 是否首选
     */
    private Boolean preferred = false;

}
