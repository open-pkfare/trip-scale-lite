package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BriefDailyRoutePlan {

  /**
   * 日期
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
   * 首选当日住宿酒店
   */
  private HotelInfo preferredHotel;

  /**
   * 当日活动列表（按时间顺序）
   */
  private List<ActivityInfo> activities;

  public BriefDailyRoutePlan(DailyRoutePlan dailyRoutePlan) {
    this.date = dailyRoutePlan.getDate();
    this.cityCode = dailyRoutePlan.getCityCode();
    this.cityName = dailyRoutePlan.getCityName();
    this.preferredHotel = dailyRoutePlan.getPreferredHotel();
    this.activities = dailyRoutePlan.getActivities();
  }
}
