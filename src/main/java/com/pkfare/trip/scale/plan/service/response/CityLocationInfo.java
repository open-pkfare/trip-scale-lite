package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class CityLocationInfo {

  /**
   * 城市名称
   */
  private String cityName;

  /**
   * 纬度
   */
  private double latitude;

  /**
   * 经度
   */
  private double longitude;

}
