package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;

import lombok.Data;

@Data
public class HotelPriceDto {
  private String currency;
  private String sellingTotal;
  private String total;
  private String base;

}
