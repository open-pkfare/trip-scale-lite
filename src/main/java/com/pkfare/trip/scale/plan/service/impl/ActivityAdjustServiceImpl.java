package com.pkfare.trip.scale.plan.service.impl;

import com.google.gson.JsonObject;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.AdjustPlanParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.plan.ActivitySearchService;
import java.util.List;
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

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripPlan tripPlan,  JsonObject adjustParam) {
    log.info("Adjusting activity, id: {}", adjustPlanParam.getId());
    
    // 验证参数
    if (tripPlan == null || adjustPlanParam == null || adjustPlanParam.getId() == null) {
      throw new TripPlanException(TripPlanErrorCodeEnum.PARAM_ERROR, "Invalid activity adjustment parameters");
    }
    
    List<ActivityInfo> activities = tripPlan.getActivities();
    if (activities == null) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND, "No activities list found in trip plan");
    }
    
    String adjustType = adjustPlanParam.getAdjustType();
    if ("remove".equals(adjustType)) {
      // 移除活动
      boolean removed = activities.removeIf(activity -> activity.getActivityId().equals(adjustPlanParam.getId()));
      if (removed) {
        log.info("Activity removed successfully: {}", adjustPlanParam.getId());
      } else {
        throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND, "Activity not found for removal: " + adjustPlanParam.getId());
      }
    } else if ("add".equals(adjustType)) {
      // 添加新活动
      ActivityInfo newActivity = activitySearchService.searchActivities(generatePlanParam, adjustPlanParam);
      if (newActivity == null) {
        throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND, "Failed to find new activity");
      }
      activities.add(newActivity);
      log.info("Activity added successfully: {}", newActivity.getActivityId());
    } else {
      // 调整现有活动
      boolean activityFound = false;
      for (int i = 0; i < activities.size(); i++) {
        ActivityInfo activity = activities.get(i);
        if (activity.getActivityId().equals(adjustPlanParam.getId())) {
          activityFound = true;
          
          // 调用搜索服务获取调整后的活动
          ActivityInfo adjustedActivity = activitySearchService.searchActivities(generatePlanParam, activity, adjustPlanParam);
          if (adjustedActivity == null) {
            throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND, "Failed to adjust activity");
          }
          
          // 更新活动信息
          activities.set(i, adjustedActivity);
          log.info("Activity adjusted successfully: {}", activity.getActivityId());
          break;
        }
      }
      
      if (!activityFound) {
        throw new TripPlanException(TripPlanErrorCodeEnum.NO_ACTIVITY_FOUND, "Activity not found: " + adjustPlanParam.getId());
      }
    }
  }
}