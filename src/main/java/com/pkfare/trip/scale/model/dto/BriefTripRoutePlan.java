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
   * 首选航班信息列表
   */
  private List<FlightInfo> preferredFlights;

  public BriefTripRoutePlan(TripRoutePlanResult dailyRoutePlan) {
    this.dailyPlans = dailyRoutePlan.getDailyPlans().stream().map(BriefDailyRoutePlan::new).collect(Collectors.toList());
    // todo 先设置为空集合
    this.preferredFlights = Collections.emptyList();
  }
}
