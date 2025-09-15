package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BriefTripRoutePlan {

  /**
   * 每日路线规划列表
   */
  private List<BriefDailyRoutePlan> dailyPlans;
  /**
   * 首选酒店列表
   */
  private List<BriefHotelInfo> hotelInfos;
  /**
   * 首选航班信息列表
   */
  private List<FlightInfo> flightInfos;

  public BriefTripRoutePlan(TripRoutePlanResult dailyRoutePlan) {
    this.dailyPlans = dailyRoutePlan.getDailyPlans().stream().map(BriefDailyRoutePlan::new).collect(Collectors.toList());
    this.hotelInfos = dailyRoutePlan.getCityHotelsInfos().stream().map(BriefHotelInfo::new).collect(Collectors.toList());
    // 先设置为空集合，暂不支持通过对话调整航班
    this.flightInfos = Collections.emptyList();
  }
}
