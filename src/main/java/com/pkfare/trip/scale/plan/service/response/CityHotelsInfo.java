package com.pkfare.trip.scale.plan.service.response;

import java.util.List;
import lombok.Data;

@Data
public class CityHotelsInfo {

  private String cityCode;

  private String cityName;

  private HotelInfo preferredHotel;

  private List<HotelInfo> alternativeHotels;



}
