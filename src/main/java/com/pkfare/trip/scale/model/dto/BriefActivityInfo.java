package com.pkfare.trip.scale.model.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class BriefActivityInfo {

  /**
   * 活动ID
   */
  private String activityId;

  /**
   * 活动名称
   */
  private String name;

  public BriefActivityInfo(ActivityInfo activityInfo) {
    this.activityId = activityInfo.getActivityId();
    this.name = activityInfo.getName();
  }
}
