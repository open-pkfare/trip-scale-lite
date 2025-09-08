package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class HotelPrice {
  private String currency;
  private String sellingTotal;
  private String total;
  private String base;

}
