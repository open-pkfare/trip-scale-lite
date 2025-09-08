package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class BriefHotelInfo {

  /**
   * 酒店ID
   */
  private String hotelId;
  /**
   * 酒店名称
   */
  private String hotelName;

  public BriefHotelInfo(HotelInfo hotelInfo) {
    this.hotelId = hotelInfo.getHotel().getHotelId();
    this.hotelName = hotelInfo.getHotel().getName();
  }
}
