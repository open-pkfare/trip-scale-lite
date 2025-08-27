package com.pkfare.trip.scale.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustService;
import com.pkfare.trip.scale.plan.service.param.AdjustItemEnum;
import com.pkfare.trip.scale.plan.service.param.AdjustPlanParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.plan.ActivitySearchService;
import com.pkfare.trip.scale.service.plan.FlightSearchService;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 旅行计划调整服务实现
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class TripPlanAdjustServiceImpl {

  @Autowired
  private FlightSearchService flightSearchService;

  @Autowired
  private HotelSearchService hotelSearchService;

  @Autowired
  private ActivitySearchService activitySearchService;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public TripPlan adjustPlan(GeneratePlanParam generatePlanParam, TripPlan tripPlan, List<AdjustPlanParam> adjustPlanParams) {
    log.info("Adjusting trip plan: {}", tripPlan.getPlanId());
    TripPlan adjustedPlan;
    try {
      // 先序列化为JSON，再反序列化为新对象，实现深拷贝
      adjustedPlan = objectMapper.readValue(objectMapper.writeValueAsString(tripPlan), TripPlan.class);
    } catch (IOException e) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_FLIGHT_FOUND, e);
    }

    for (AdjustPlanParam adjustParam : adjustPlanParams) {
      try {
        Optional<AdjustItemEnum> item = AdjustItemEnum.getByCode(adjustParam.getItem());
        if (item.isEmpty()) {
          log.warn("Unknown adjust item type: {}", adjustParam.getItem());
          continue;
        }

        switch (item.get()) {
          case FLIGHT:
            adjustFlight(generatePlanParam, adjustedPlan, adjustParam);
            break;
          case HOTEL:
            adjustHotel(adjustedPlan, adjustParam);
            break;
          case ACTIVITY:
            adjustActivity(adjustedPlan, adjustParam);
            break;
        }
      } catch (Exception e) {
        log.warn("Failed to process adjust item: {}", adjustParam.getItem(), e);
      }
    }
    // 重新计算总费用
    recalculateTotalCost(adjustedPlan);

    log.info("Trip plan adjusted successfully: {}", adjustedPlan.getPlanId());
    return adjustedPlan;
  }

  public void adjustFlight(GeneratePlanParam generatePlanParam, TripPlan tripPlan, AdjustPlanParam adjustPlanParam) {
    log.info("Adjusting flight, id: {}", adjustPlanParam.getId());
    List<FlightInfo> flights = tripPlan.getFlights();
    for (int i = 0; i < flights.size(); i++) {
      FlightInfo flight = flights.get(i);
      if (flight.getId().equals(adjustPlanParam.getId())) {
        // 调用搜索服务获取新航班
        FlightInfo newFlight = flightSearchService.searchFlightInfo(generatePlanParam, flight, adjustPlanParam);
        flights.set(i, newFlight);
        break;
      }
    }
  }

  @Override
  public TripPlan adjustHotel(TripPlan tripPlan, AdjustPlanParam adjustPlanParam) {
    log.info("Adjusting hotel: {}", adjustPlanParam.getId());

    // 实现酒店调整逻辑
    // 1. 找到需要调整的酒店
    // 2. 根据调整类型执行相应操作
    // 3. 调用hotelSearchService搜索新酒店
    // 4. 更新旅行计划中的酒店信息

    // 示例实现（简化版）
    List<HotelInfo> hotels = tripPlan.getHotels();
    for (int i = 0; i < hotels.size(); i++) {
      HotelInfo hotel = hotels.get(i);
      if (hotel.getHotelId().equals(adjustPlanParam.getId())) {
        // 调用搜索服务获取新酒店
        // HotelInfo newHotel = hotelSearchService.searchHotels(...);
        // hotels.set(i, newHotel);
        break;
      }
    }

    return tripPlan;
  }

  @Override
  public TripPlan adjustActivity(TripPlan tripPlan, AdjustPlanParam adjustPlanParam) {
    log.info("Adjusting activity: {}", adjustPlanParam.getId());

    // 实现活动调整逻辑
    // 1. 找到需要调整的活动
    // 2. 根据调整类型执行相应操作（增加或取消）
    // 3. 如果是增加，调用activitySearchService搜索新活动
    // 4. 更新旅行计划中的活动信息

    // 示例实现（简化版）
    List<ActivityInfo> activities = tripPlan.getActivities();
    if ("remove".equals(adjustPlanParam.getAdjustType())) {
      activities.removeIf(activity -> activity.getActivityId().equals(adjustPlanParam.getId()));
    } else if ("add".equals(adjustPlanParam.getAdjustType())) {
      // 调用搜索服务获取新活动
      // ActivityInfo newActivity = activitySearchService.searchActivities(...);
      // activities.add(newActivity);
    }

    return tripPlan;
  }

  /**
   * 重新计算总费用
   */
  private void recalculateTotalCost(TripPlan tripPlan) {
    // 实现总费用计算逻辑
    // 这里只是示例，实际计算可能需要更复杂的逻辑
    // tripPlan.setTotalCost(...);
  }
}