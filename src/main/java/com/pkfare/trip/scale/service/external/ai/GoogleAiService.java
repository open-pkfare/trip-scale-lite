package com.pkfare.trip.scale.service.external.ai;

import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class GoogleAiService {

  public String generateAiPlan(SubmitAiPlanInfo planInfo) {
    // 基于航班、酒店、景点活动等信息（包含经纬度），通过调用google map api获取路线规划，返回路线规划结果

    return null;
  }

}
