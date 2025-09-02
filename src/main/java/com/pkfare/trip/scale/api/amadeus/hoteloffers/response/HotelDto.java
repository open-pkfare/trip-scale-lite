package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;

import lombok.Data;

@Data
public class HotelDto {

  private String type;
  private String hotelId;
  private String chainCode;
  private String brandCode;
  private String dupeId;
  private String name;
  private String cityCode;
  private double latitude;
  private double longitude;

}
