package com.pkfare.trip.scale.plan.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.ActivityAdjustTypeEnum;
import com.pkfare.trip.scale.plan.service.param.AdjustActivityParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.service.plan.ActivitySearchService;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 活动调整服务实现类
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class ActivityAdjustServiceImpl implements TripPlanAdjustInterface {

  @Autowired
  private ActivitySearchService activitySearchService;
  @Autowired
  private GoogleAiService googleAiService;
  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonNode adjustParam) {
    if (Objects.isNull(generatePlanParam) || Objects.isNull(tripPlan) || Objects.isNull(adjustParam)) {
      throw new TripPlanException(TripPlanErrorCodeEnum.PARAM_ERROR);
    }
    AdjustActivityParam adjustActivityParam = objectMapper.convertValue(adjustParam, AdjustActivityParam.class);
    log.info("Adjusting activity param: {}", adjustActivityParam);

    List<DailyRoutePlan> dailySchedules = tripPlan.getDailyPlans();
    for (DailyRoutePlan routePlan : dailySchedules) {
      // 日期检查
      if (!routePlan.getDate().equals(adjustActivityParam.getDate())) {
        continue;
      }
      Optional<ActivityAdjustTypeEnum> optional = ActivityAdjustTypeEnum.getByCode(adjustActivityParam.getAdjustType());
      if (optional.isEmpty()) {
        throw new TripPlanException(TripPlanErrorCodeEnum.PARAM_ERROR.getCode(), "Invalid adjust type: " + adjustActivityParam.getAdjustType());
      }
      // 区分adjustType
      ActivityAdjustTypeEnum adjustTypeEnum = optional.get();
      if (ActivityAdjustTypeEnum.REPLACE.equals(adjustTypeEnum)) {
        doReplace(tripPlan, routePlan, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.ADD.equals(adjustTypeEnum)) {
        doAdd(tripPlan, routePlan, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.REDUCE.equals(adjustTypeEnum)) {
        doReduce(tripPlan, routePlan, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.CHEAPER.equals(adjustTypeEnum)) {
        doCheaper(tripPlan, routePlan, adjustActivityParam);
      } else {
        throw new TripPlanException(TripPlanErrorCodeEnum.UNSUPPORTED_ACTIVITY_ADJUSTMENT_TYPE);
      }
      try {
        googleAiService.generateRoutes(routePlan, routePlan.getActivities());
      } catch (Exception e) {
        throw new TripPlanException(TripPlanErrorCodeEnum.OPTIMIZE_ACTIVITY_FAILED, e);
      }
      break;
    }
  }

  private void doReplace(TripRoutePlanResult tripPlan, DailyRoutePlan schedule, AdjustActivityParam adjustActivityParam) {
    boolean found = false;
    List<ActivityInfo> activities = schedule.getActivities();

    for (int i = 0; i < activities.size(); i++) {
      ActivityInfo activity = activities.get(i);
      if (activity.getActivityId().equals(adjustActivityParam.getId())) {
        Optional<ActivityInfo> optional = searchActivities(tripPlan, schedule, adjustActivityParam);
        if (optional.isEmpty()) {
          throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
        }
        ActivityInfo newActivity = optional.get();
        activities.set(i, newActivity);
        found = true;
        break;
      }
    }
    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
    }
    log.info("Replace activity successful");
  }

  private Optional<ActivityInfo> searchActivities(TripRoutePlanResult tripPlan, DailyRoutePlan schedule, AdjustActivityParam adjustActivityParam) {
    List<ActivityInfo> activities = activitySearchService.searchActivitiesNearby(schedule.getPreferredHotel(), adjustActivityParam.getActivityType(),
        schedule.getPreferredHotel().getCurrency());
    if (activities.isEmpty()) {
      return Optional.empty();
    }
    Set<String> idSet = tripPlan.getDailyPlans().stream()
        .flatMap(dailyPlan -> dailyPlan.getActivities().stream())
        .map(ActivityInfo::getActivityId).collect(Collectors.toSet());
    for (ActivityInfo activityInfo : activities) {
      if (idSet.contains(activityInfo.getActivityId())) {
        continue;
      }
      if (Objects.nonNull(adjustActivityParam.getMaxPrice()) && activityInfo.getPrice().compareTo(adjustActivityParam.getMaxPrice()) > 0) {
        continue;
      }
      try {
        log.info("Found activity: {}", objectMapper.writeValueAsString(activityInfo));
      } catch (JsonProcessingException e) {
      }
      return Optional.of(activityInfo);
    }
    return Optional.empty();
  }

  private void doAdd(TripRoutePlanResult tripPlan, DailyRoutePlan schedule, AdjustActivityParam adjustActivityParam) {
    Optional<ActivityInfo> optional = searchActivities(tripPlan, schedule, adjustActivityParam);
    if (optional.isEmpty()) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
    }
    ActivityInfo newActivity = optional.get();
    schedule.getActivities().add(newActivity);
    log.info("Add activity successful");
  }

  private void doReduce(TripRoutePlanResult tripPlan, DailyRoutePlan schedule, AdjustActivityParam adjustActivityParam) {
    List<ActivityInfo> activities = schedule.getActivities();
    // 随机移除一个
    if (adjustActivityParam.getId() == null) {
      int randomIndex = ThreadLocalRandom.current().nextInt(activities.size());
      activities.remove(randomIndex);
      return;
    }
    Iterator<ActivityInfo> iterator = activities.iterator();
    while (iterator.hasNext()) {
      ActivityInfo activity = iterator.next();
      if (activity.getActivityId().equals(adjustActivityParam.getId())) {
        iterator.remove();
        break;
      }
    }
  }

  private void doCheaper(TripRoutePlanResult tripPlan, DailyRoutePlan schedule, AdjustActivityParam adjustActivityParam) {
    List<ActivityInfo> activities = schedule.getActivities();
    // 设置限价
    ActivityInfo oldActivity = null;
    if (Objects.nonNull(adjustActivityParam.getId())) {
      for (int i = 0; i < activities.size(); i++) {
        ActivityInfo activity = activities.get(i);
        if (!activity.getActivityId().equals(adjustActivityParam.getId())) {
          continue;
        }
        if (Objects.nonNull(adjustActivityParam.getMaxPrice())) {
          adjustActivityParam.setMaxPrice(adjustActivityParam.getMaxPrice().min(activity.getPrice()));
        } else {
          adjustActivityParam.setMaxPrice(activity.getPrice());
        }
        oldActivity = activity;
        break;
      }
    }
    Optional<ActivityInfo> optional = searchActivities(tripPlan, schedule, adjustActivityParam);
    if (optional.isEmpty()) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
    }
    ActivityInfo newActivity = optional.get();
    if (Objects.nonNull(oldActivity)) {
      activities.set(activities.indexOf(oldActivity), newActivity);
    } else {
      activities.sort(Comparator.comparing(ActivityInfo::getPrice));
      activities.set(activities.size() - 1, newActivity);
    }
    log.info("doCheaper  activity successful");
  }
}