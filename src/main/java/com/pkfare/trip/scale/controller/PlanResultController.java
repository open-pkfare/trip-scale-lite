package com.pkfare.trip.scale.controller;

import com.pkfare.trip.scale.service.PlanResultCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 计划结果控制器
 * 提供通过planResultId获取planResultJson的API接口
 *
 * @author Trip Scale Team
 */
@Slf4j
@RestController
@RequestMapping("/api/plan-result")
@CrossOrigin(origins = "*")
public class PlanResultController {

    @Autowired
    private PlanResultCacheService planResultCacheService;

    /**
     * 通过planResultId获取planResultJson
     *
     * @param planResultId 计划结果ID
     * @return 计划结果JSON字符串
     */
    @GetMapping("/{planResultId}")
    public ResponseEntity<String> getPlanResult(@PathVariable String planResultId) {
        log.info("Received request to get plan result for ID: {}", planResultId);
        
        try {
            // 验证planResultId参数
            if (planResultId == null || planResultId.trim().isEmpty()) {
                log.warn("Invalid planResultId: {}", planResultId);
                return ResponseEntity.badRequest()
                    .body("{\"error\":\"Invalid planResultId\",\"message\":\"planResultId cannot be null or empty\"}");
            }

            // 从缓存中获取planResultJson
            String planResultJson = planResultCacheService.getPlanResult(planResultId);
            
            if (planResultJson != null) {
                log.info("Successfully retrieved plan result for ID: {}", planResultId);
                return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(planResultJson);
            } else {
                log.warn("Plan result not found for ID: {}", planResultId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving plan result for ID: {}", planResultId, e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"Internal server error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 检查planResultId是否存在
     *
     * @param planResultId 计划结果ID
     * @return 是否存在
     */
    @GetMapping("/{planResultId}/exists")
    public ResponseEntity<Boolean> checkPlanResultExists(@PathVariable String planResultId) {
        log.info("Received request to check existence of plan result for ID: {}", planResultId);
        
        try {
            if (planResultId == null || planResultId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(false);
            }

            boolean exists = planResultCacheService.containsPlanResult(planResultId);
            log.info("Plan result existence check for ID: {} - exists: {}", planResultId, exists);
            
            return ResponseEntity.ok(exists);
            
        } catch (Exception e) {
            log.error("Error checking plan result existence for ID: {}", planResultId, e);
            return ResponseEntity.internalServerError().body(false);
        }
    }

    /**
     * 删除缓存的计划结果
     *
     * @param planResultId 计划结果ID
     * @return 删除结果
     */
    @DeleteMapping("/{planResultId}")
    public ResponseEntity<String> deletePlanResult(@PathVariable String planResultId) {
        log.info("Received request to delete plan result for ID: {}", planResultId);
        
        try {
            if (planResultId == null || planResultId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("{\"error\":\"Invalid planResultId\",\"message\":\"planResultId cannot be null or empty\"}");
            }

            boolean deleted = planResultCacheService.removePlanResult(planResultId);
            
            if (deleted) {
                log.info("Successfully deleted plan result for ID: {}", planResultId);
                return ResponseEntity.ok()
                    .body("{\"success\":true,\"message\":\"Plan result deleted successfully\"}");
            } else {
                log.warn("Failed to delete plan result for ID: {}", planResultId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error deleting plan result for ID: {}", planResultId, e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"Internal server error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<String> getCacheStats() {
        log.info("Received request to get cache statistics");
        
        try {
            String stats = planResultCacheService.getCacheStats();
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body("{\"stats\":\"" + stats + "\"}");
                
        } catch (Exception e) {
            log.error("Error getting cache statistics", e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"Internal server error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 清空所有缓存的计划结果
     *
     * @return 清空结果
     */
    @DeleteMapping("/cache/clear")
    public ResponseEntity<String> clearAllPlanResults() {
        log.info("Received request to clear all plan results from cache");
        
        try {
            planResultCacheService.clearAllPlanResults();
            log.info("Successfully cleared all plan results from cache");
            return ResponseEntity.ok()
                .body("{\"success\":true,\"message\":\"All plan results cleared successfully\"}");
                
        } catch (Exception e) {
            log.error("Error clearing all plan results from cache", e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"Internal server error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
