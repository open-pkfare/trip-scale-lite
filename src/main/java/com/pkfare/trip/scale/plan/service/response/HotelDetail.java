package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class HotelDetail {

  private String type;
  private String hotelId;
  private String chainCode;
  private String brandCode;
  private String dupeId;
  private String name;
  private String cityCode;
  private String cityName;
  private double latitude;
  private double longitude;

}
