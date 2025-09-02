package com.pkfare.trip.scale.plan.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.ActivityAdjustTypeEnum;
import com.pkfare.trip.scale.plan.service.param.AdjustActivityParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailySchedule;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
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

  private static final Gson gson = new Gson();
  @Autowired
  private ActivitySearchService activitySearchService;

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripPlan tripPlan, JsonObject adjustParam) {
    // todo 参数检查
    if (Objects.isNull(generatePlanParam) || Objects.isNull(tripPlan) || Objects.isNull(adjustParam)) {
      throw new TripPlanException(TripPlanErrorCodeEnum.PARAM_ERROR);
    }
    AdjustActivityParam adjustActivityParam = gson.fromJson(adjustParam, AdjustActivityParam.class);
    log.info("Adjusting activity param: {}", adjustActivityParam);

    List<DailySchedule> dailySchedules = tripPlan.getDailySchedules();
    for (DailySchedule schedule : dailySchedules) {
      // 日期检查
      if (!schedule.getDate().equals(adjustActivityParam.getDate())) {
        continue;
      }
      Optional<ActivityAdjustTypeEnum> optional = ActivityAdjustTypeEnum.getByCode(adjustActivityParam.getAdjustType());
      if (optional.isEmpty()) {
        throw new TripPlanException(TripPlanErrorCodeEnum.PARAM_ERROR.getCode(), "Invalid adjust type: " + adjustActivityParam.getAdjustType());
      }
      // 区分adjustType
      ActivityAdjustTypeEnum adjustTypeEnum = optional.get();
      if (ActivityAdjustTypeEnum.REPLACE.equals(adjustTypeEnum)) {
        doReplace(tripPlan, schedule, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.ADD.equals(adjustTypeEnum)) {
        doAdd(tripPlan, schedule, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.REDUCE.equals(adjustTypeEnum)) {
        doReduce(tripPlan, schedule, adjustActivityParam);
      } else if (ActivityAdjustTypeEnum.CHEAPER.equals(adjustTypeEnum)) {
        doCheaper(tripPlan, schedule, adjustActivityParam);
      } else {
        throw new TripPlanException(TripPlanErrorCodeEnum.UNSUPPORTED_ACTIVITY_ADJUSTMENT_TYPE);
      }
      break;
    }
    // todo 刷新行程

  }

  private void doReplace(TripPlan tripPlan, DailySchedule schedule, AdjustActivityParam adjustActivityParam) {
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

        List<ActivityInfo> activities1 = tripPlan.getActivities();
        for (int j = 0; j < activities1.size(); j++) {
          if (activities1.get(j).getActivityId().equals(activity.getActivityId())) {
            activities1.set(j, newActivity);
          }
        }
        break;
      }
    }
    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
    }
  }

  private Optional<ActivityInfo> searchActivities(TripPlan tripPlan, DailySchedule schedule, AdjustActivityParam adjustActivityParam) {
    List<ActivityInfo> activities = activitySearchService.searchActivitiesNearby(schedule.getHotel());
    if (activities.isEmpty()) {
      return Optional.empty();
    }
    Set<String> idSet = tripPlan.getActivities().stream().map(ActivityInfo::getActivityId).collect(Collectors.toSet());
    for (ActivityInfo activityInfo : activities) {
      if (idSet.contains(activityInfo.getActivityId())) {
        continue;
      }
      if (Objects.nonNull(adjustActivityParam.getMaxPrice()) && activityInfo.getPrice().compareTo(adjustActivityParam.getMaxPrice()) > 0) {
        continue;
      }
      return Optional.of(activityInfo);
    }
    return Optional.empty();
  }

  private void doAdd(TripPlan tripPlan, DailySchedule schedule, AdjustActivityParam adjustActivityParam) {
    Optional<ActivityInfo> optional = searchActivities(tripPlan, schedule, adjustActivityParam);
    if (optional.isEmpty()) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND);
    }
    ActivityInfo newActivity = optional.get();
    schedule.getActivities().add(newActivity);
    tripPlan.setActivities(schedule.getActivities());
  }

  private void doReduce(TripPlan tripPlan, DailySchedule schedule, AdjustActivityParam adjustActivityParam) {
    List<ActivityInfo> activities = schedule.getActivities();
    // 随机移除一个
    if (adjustActivityParam.getId() == null) {
      int randomIndex = ThreadLocalRandom.current().nextInt(activities.size());
      tripPlan.getActivities().remove(activities.get(randomIndex));
      activities.remove(randomIndex);
      return;
    }
    Iterator<ActivityInfo> iterator = activities.iterator();
    while (iterator.hasNext()) {
      ActivityInfo activity = iterator.next();
      if (activity.getActivityId().equals(adjustActivityParam.getId())) {
        iterator.remove();
        tripPlan.getActivities().remove(activity);
        break;
      }
    }
  }

  private void doCheaper(TripPlan tripPlan, DailySchedule schedule, AdjustActivityParam adjustActivityParam) {
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
      oldActivity = activities.getLast();
      activities.set(activities.size() - 1, newActivity);
    }

    List<ActivityInfo> activities1 = tripPlan.getActivities();
    for (int j = 0; j < activities1.size(); j++) {
      if (activities1.get(j).getActivityId().equals(oldActivity.getActivityId())) {
        activities1.set(j, newActivity);
      }
    }
  }
}