package com.pkfare.trip.scale.plan.service.response;


import lombok.Data;

@Data
public class FlightLocationInfo {

  /**
   * 机场
   */
  private String airport;

  private GeoInfo geoInfo;

}
