package com.pkfare.trip.scale.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.impl.ActivityAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.impl.FlightAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.impl.HotelAdjustServiceImpl;
import com.pkfare.trip.scale.plan.service.param.AdjustItemEnum;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.util.JsonUtil;
import java.io.IOException;
import java.util.ArrayList;
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
public class TripPlanAdjustService {

  @Autowired
  private FlightAdjustServiceImpl flightAdjustService;

  @Autowired
  private HotelAdjustServiceImpl hotelAdjustService;

  @Autowired
  private ActivityAdjustServiceImpl activityAdjustService;

  @Autowired
  private GoogleAiService googleAiService;

  @Autowired
  private ObjectMapper objectMapper;

  public TripRoutePlanResult adjustPlan(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonNode adjustPlanParams) {
    log.info("Adjust plan request: {}", adjustPlanParams);
    long start = System.currentTimeMillis();
    TripRoutePlanResult adjustedPlan;
    try {
      // 先序列化为JSON，再反序列化为新对象，实现深拷贝
      adjustedPlan = objectMapper.readValue(objectMapper.writeValueAsString(tripPlan), TripRoutePlanResult.class);
    } catch (IOException e) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_FLIGHT_FOUND, e);
    }

    List<JsonNode> sortedList = sort(adjustPlanParams);
    for (JsonNode element : sortedList) {
      try {
        if (!element.isObject()) {
          log.warn("Invalid adjust param format: {}", element);
          continue;
        }
        String item = element.get("item").asText();
        Optional<AdjustItemEnum> itemEnum = AdjustItemEnum.getByCode(item);
        if (itemEnum.isEmpty()) {
          log.warn("Unknown adjust item type: {}", item);
          continue;
        }

        switch (itemEnum.get()) {
          case FLIGHT:
            flightAdjustService.adjust(generatePlanParam, adjustedPlan, element);
            break;
          case HOTEL:
            hotelAdjustService.adjust(generatePlanParam, adjustedPlan, element);
            break;
          case ACTIVITY:
            activityAdjustService.adjust(generatePlanParam, adjustedPlan, element);
            break;
        }
      } catch (Exception e) {
        log.error("Failed to process adjust item: {}", element, e);
        throw new TripPlanException(TripPlanErrorCodeEnum.SERVER_ERROR, e);
      }
    }

    // 计算总距离和总时间
    List<DailyRoutePlan> dailyPlans = adjustedPlan.getDailyPlans();
    long totalDistance = dailyPlans.stream()
        .filter(plan -> plan.getTotalDistance() != null)
        .mapToLong(DailyRoutePlan::getTotalDistance)
        .sum();
    long totalDuration = dailyPlans.stream()
        .filter(plan -> plan.getTotalDuration() != null)
        .mapToLong(DailyRoutePlan::getTotalDuration)
        .sum();

    adjustedPlan.setTotalDistance(totalDistance);
    adjustedPlan.setTotalDuration(totalDuration);
    adjustedPlan.setSummary(googleAiService.generateSummary(dailyPlans));

    long totalTime = System.currentTimeMillis() - start;
    log.info("Total trip plan adjust time: {} ms", totalTime);
    log.info("Generated trip plan JSON: {}", JsonUtil.toJson(dailyPlans.getFirst()));
    return adjustedPlan;
  }

  // 按照调整项类型的优先级排序：航班 -> 酒店 -> 活动
  private List<JsonNode> sort(JsonNode adjustPlanParams) {
    List<JsonNode> resultList = new ArrayList<>();
    if (adjustPlanParams.isArray()) {
      for (JsonNode node : adjustPlanParams) {
        resultList.add(node);
      }

      resultList.sort((param1, param2) -> {
        String item1 = param1.get("item").asText();
        String item2 = param2.get("item").asText();

        // 定义调整项的优先级
        int priority1 = AdjustItemEnum.getByCode(item1).map(AdjustItemEnum::getPriority).orElse(0);
        int priority2 = AdjustItemEnum.getByCode(item2).map(AdjustItemEnum::getPriority).orElse(0);
        return Integer.compare(priority1, priority2);
      });
    }
    return resultList;
  }
}