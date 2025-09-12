package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
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
   * 首选当日住宿酒店
   */
//  private BriefHotelInfo preferredHotel;

  /**
   * 当日活动列表（按时间顺序）
   */
  private List<BriefActivityInfo> activities;

  public BriefDailyRoutePlan(DailyRoutePlan dailyRoutePlan) {
    this.date = dailyRoutePlan.getDate();
    this.cityCode = dailyRoutePlan.getCityCode();
//    this.preferredHotel = new BriefHotelInfo(dailyRoutePlan.getPreferredHotel());
    this.activities = dailyRoutePlan.getActivities().stream().map(BriefActivityInfo::new).collect(Collectors.toList());
  }
}
