package com.pkfare.trip.scale.controller;

import com.pkfare.trip.scale.plan.service.TripPlanAdjustService;
import com.pkfare.trip.scale.plan.service.param.AdjustPlanRequest;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旅行计划调整控制器
 *
 * @author Trip Scale Team
 */
@Deprecated
@Slf4j
@RestController
@RequestMapping("/trip-plan")
@CrossOrigin(origins = "*")
public class TripPlanAdjustController {

  @Autowired
  private TripPlanAdjustService tripPlanAdjustService;

  /**
   * 调整旅行计划
   *
   * @param request 调整计划请求
   * @return 调整后的旅行计划
   */
  @PostMapping("/adjust")
  public ResponseEntity<TripRoutePlanResult> adjustPlan(@Valid @RequestBody AdjustPlanRequest request) {
    TripRoutePlanResult tripPlan = request.getTripPlan();
    try {
      TripRoutePlanResult adjustedPlan = tripPlanAdjustService.adjustPlan(request.getGeneratePlanParam(), tripPlan, request.getAdjustPlanParams());
      log.info("Trip plan adjusted successfully");
      return ResponseEntity.ok(adjustedPlan);
    } catch (Exception e) {
      log.error("Failed to adjust trip plan", e);

      // 返回错误响应
      TripRoutePlanResult error = new TripRoutePlanResult();
      error.setStatus("ERROR");
      error.setErrorMessage("调整旅行计划时发生错误: " + e.getMessage());
      return ResponseEntity.status(500).body(error);
    }
  }
}