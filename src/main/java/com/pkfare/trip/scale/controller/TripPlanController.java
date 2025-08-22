package com.pkfare.trip.scale.controller;

import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 旅行计划控制器
 * 
 * @author Trip Scale Team
 */
@Slf4j
@RestController
@RequestMapping("/api/trip-plan")
@CrossOrigin(origins = "*")
public class TripPlanController {
    
    @Autowired
    private GeneratePlanService generatePlanService;
    
    /**
     * 生成旅行计划
     * 
     * @param param 生成计划参数
     * @return 旅行计划
     */
    @PostMapping("/generate")
    public ResponseEntity<String> generatePlan(@Valid @RequestBody GeneratePlanParam param) {
        log.info("Received trip plan generation request for origin: {}, routes: {}", 
            param.getOrigin(), param.getTrip_routes().size());
        
        try {
            String tripPlan = generatePlanService.generatePlan(param);

            return ResponseEntity.ok(tripPlan);
            
        } catch (Exception e) {
            log.error("Failed to generate trip plan", e);
            
            // 返回错误响应
            TripPlan errorPlan = new TripPlan();
            errorPlan.setPlanId(java.util.UUID.randomUUID().toString());
            errorPlan.setStatus(com.pkfare.trip.scale.model.enums.PlanStatus.API_ERROR);
            errorPlan.setErrorMessage("生成旅行计划时发生错误: " + e.getMessage());
            errorPlan.setCreatedTime(java.time.LocalDateTime.now());
            errorPlan.setCurrency(param.getCurrency());
            
            return ResponseEntity.status(500).body(errorPlan.toString());
        }
    }
    
    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Trip Plan Service is running");
    }
}
