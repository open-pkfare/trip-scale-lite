package com.pkfare.trip.scale.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.impl.ActivityAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.impl.FlightAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.impl.HotelAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.param.AdjustItemEnum;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import java.io.IOException;
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
public class TripPlanAdjustService {
  @Autowired
  private FlightAdjustServiceImpl flightAdjustService;

  @Autowired
  private HotelAdjustServiceImpl hotelAdjustService;

  @Autowired
  private ActivityAdjustServiceImpl activityAdjustService;

  @Autowired
  private ObjectMapper objectMapper;

  public TripRoutePlanResult adjustPlan(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonArray adjustPlanParams) {
    TripRoutePlanResult adjustedPlan;
    try {
      // 先序列化为JSON，再反序列化为新对象，实现深拷贝
      adjustedPlan = objectMapper.readValue(objectMapper.writeValueAsString(tripPlan), TripRoutePlanResult.class);
    } catch (IOException e) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_FLIGHT_FOUND, e);
    }

    for (JsonElement element : adjustPlanParams) {
      try {
        if (!element.isJsonObject()) {
          log.warn("Invalid adjust param format: {}", element);
          continue;
        }
        JsonObject adjustParam = element.getAsJsonObject();
        String item = adjustParam.get("item").getAsString();
        Optional<AdjustItemEnum> itemEnum = AdjustItemEnum.getByCode(item);
        if (itemEnum.isEmpty()) {
          log.warn("Unknown adjust item type: {}", item);
          continue;
        }

        switch (itemEnum.get()) {
          case FLIGHT:
            flightAdjustService.adjust(generatePlanParam, adjustedPlan, adjustParam);
            break;
          case HOTEL:
            hotelAdjustService.adjust(generatePlanParam, adjustedPlan, adjustParam);
            break;
          case ACTIVITY:
            activityAdjustService.adjust(generatePlanParam, adjustedPlan, adjustParam);
            break;
        }
      } catch (Exception e) {
        log.warn("Failed to process adjust item: {}", element, e);
      }
    }
    // todo 重新计算总费用
//    recalculateTotalCost(adjustedPlan);
    return adjustedPlan;
  }

  /**
   * 重新计算总费用
   */
  private void recalculateTotalCost(TripPlan tripPlan) {
    // todo
  }
}