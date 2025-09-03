package com.pkfare.trip.scale.api.amadeus.activities.request;


import lombok.Data;

@Data
public class ActivitiesSearchRequest {

  private Double latitude;  // 纬度（十进制坐标）
  private Double longitude;  // 经度（十进制坐标）
  private int radius;        // 搜索半径以公里为单位。取值范围为0 ~ 20，默认值为1公里。
  /**
   * 活动类型
   * @see com.pkfare.trip.scale.plan.service.param.ActivityTypeEnum
   */
  private String categoryGroup;
}
