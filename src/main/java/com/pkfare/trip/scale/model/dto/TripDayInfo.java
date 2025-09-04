package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripDayInfo {

  /**
   * 日期，格式为yyyy-MM-dd
   */
  private LocalDate date;

  /**
   * 城市代码
   */
  private String cityCode;

  /**
   * 城市名称
   */
  private String cityName;

  /**
   * 第几天
   */
  private int dayOfTrip;

  public TripDayInfo(DailyRoutePlan routePlan, int dayOfTrip) {
    this.date = routePlan.getDate();
    this.cityCode = routePlan.getCityCode();
    this.cityName = routePlan.getCityName();
    this.dayOfTrip = dayOfTrip;
  }
}
