package com.pkfare.trip.scale.plan.service.response;

import java.util.List;
import lombok.Data;

@Data
public class CityHotelsInfo {

  private String cityCode;

  private String cityName;
  /**
   * 入住日期，格式yyyy-MM-dd
   */
  private String checkInDate;

  private HotelInfo preferredHotel;

  private List<HotelInfo> alternativeHotels;



}
