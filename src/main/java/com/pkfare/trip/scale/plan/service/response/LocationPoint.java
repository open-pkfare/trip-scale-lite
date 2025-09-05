package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class LocationPoint {

  private String name;
  private double latitude;
  private double longitude;

  public LocationPoint() {
  }

  public LocationPoint(String hotelName, double latitude, double longitude) {
  }
}
