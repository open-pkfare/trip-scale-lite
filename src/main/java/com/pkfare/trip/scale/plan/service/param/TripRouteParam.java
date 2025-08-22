package com.pkfare.trip.scale.plan.service.param;


import lombok.Data;

@Data
public class TripRouteParam {

  private int stay_days;
  private String destination_city;
  private String country_code;
  private String location_code;
  private String reason_for_recommendation;
}
