package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.CityHotelsInfo;
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


  private String cityCode;

  private String cityName;

  public BriefHotelInfo(HotelInfo hotelInfo) {
    this.hotelId = hotelInfo.getHotel().getHotelId();
    this.hotelName = hotelInfo.getHotel().getName();
    this.cityCode = hotelInfo.getHotel().getCityCode();
    this.cityName = hotelInfo.getHotel().getCityName();
  }

  public BriefHotelInfo(CityHotelsInfo cityHotelsInfo) {
    this.hotelId = cityHotelsInfo.getPreferredHotel().getHotel().getHotelId();
    this.hotelName = cityHotelsInfo.getPreferredHotel().getHotel().getName();
    this.cityCode = cityHotelsInfo.getPreferredHotel().getHotel().getCityCode();
    this.cityName = cityHotelsInfo.getPreferredHotel().getHotel().getCityName();
  }
}
