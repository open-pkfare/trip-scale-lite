package com.pkfare.trip.scale.plan.service.response;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CityPlanData {

  private String cityCode;
  private String cityName;
  private int stayDays;
  private List<HotelInfo> hotels;
  /**
   * key -> date
   * value -> List<ActivityInfo>
   */
  private Map<String ,List<ActivityInfo>> activities;

}
