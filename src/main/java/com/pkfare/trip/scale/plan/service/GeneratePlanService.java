package com.pkfare.trip.scale.plan.service;

import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneratePlanService {

  public void generatePlan(GeneratePlanParam param){
    /**
     * 低价预测，时间区间+OD，那一天有低价
     *
     * 策略
     *
     * 查机票接口
     *
     * 查酒店接口
     *
     * 根据时间+地点
     * activiity，活动，不是景点，门票信息
     *
     * 如果活动太多，调用ai接口筛选热门活动。（adk 写感兴趣 agent，很简单）
     *
     * 规划路线接口 google map 返回规划路线
     *
     * 返回一个计划
     */

  }


}
