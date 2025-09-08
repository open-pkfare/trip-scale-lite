package com.pkfare.trip.scale.plan.service.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LocationPoint {

  private String name;
  private double latitude;
  private double longitude;

}
