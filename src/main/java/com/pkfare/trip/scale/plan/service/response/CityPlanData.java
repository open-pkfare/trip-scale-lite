package com.pkfare.trip.scale.plan.service.response;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import java.util.List;
import lombok.Data;

@Data
public class CityPlanData {

  private String cityCode;
  private String cityName;
  private int stayDays;
  private List<HotelInfo> hotels;
  private List<ActivityInfo> activities;

}
