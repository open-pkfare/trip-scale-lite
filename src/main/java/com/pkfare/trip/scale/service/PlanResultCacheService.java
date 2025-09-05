package com.pkfare.trip.scale.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 计划结果缓存服务
 * 用于管理旅行计划结果的缓存存储和检索
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class PlanResultCacheService {

    @Autowired
    @Qualifier("planResultsCache")
    private Cache<String, String> planResultsCache;

    /**
     * 生成计划结果ID并缓存计划结果JSON
     * 
     * @param planResultJson 计划结果JSON字符串
     * @return 生成的计划结果ID
     */
    public String cachePlanResult(String planResultJson) {
        // 生成UUID作为planResultId
        String planResultId = UUID.randomUUID().toString();
        
        // 将planResultId和planResultJson的映射关系缓存到本地
        planResultsCache.put(planResultId, planResultJson);

        log.info("Successfully cached plan result with ID: {}, JSON length: {}",
                planResultId, planResultJson.length());

        return planResultId;
    }

    /**
     * 通过计划结果ID获取计划结果JSON
     * 
     * @param planResultId 计划结果ID
     * @return 计划结果JSON字符串，如果未找到则返回null
     */
    public String getPlanResult(String planResultId) {
        if (planResultId == null || planResultId.trim().isEmpty()) {
            log.warn("Attempting to get plan result with null or empty ID");
            return null;
        }

        try {
            String planResultJson = planResultsCache.getIfPresent(planResultId);
            
            if (planResultJson != null) {
                log.info("Successfully retrieved plan result for ID: {}, JSON length: {}", 
                        planResultId, planResultJson.length());
            } else {
                log.warn("Plan result not found for ID: {}", planResultId);
            }
            
            return planResultJson;
            
        } catch (Exception e) {
            log.error("Failed to retrieve plan result for ID: {}", planResultId, e);
            return null;
        }
    }

    /**
     * 检查计划结果ID是否存在
     * 
     * @param planResultId 计划结果ID
     * @return 是否存在
     */
    public boolean containsPlanResult(String planResultId) {
        if (planResultId == null || planResultId.trim().isEmpty()) {
            return false;
        }

        try {
            return planResultsCache.getIfPresent(planResultId) != null;
        } catch (Exception e) {
            log.error("Failed to check plan result existence for ID: {}", planResultId, e);
            return false;
        }
    }

    /**
     * 删除缓存的计划结果
     * 
     * @param planResultId 计划结果ID
     * @return 是否删除成功
     */
    public boolean removePlanResult(String planResultId) {
        if (planResultId == null || planResultId.trim().isEmpty()) {
            log.warn("Attempting to remove plan result with null or empty ID");
            return false;
        }

        try {
            // 先检查是否存在
            boolean exists = planResultsCache.getIfPresent(planResultId) != null;
            if (!exists) {
                log.warn("Plan result not found for deletion, ID: {}", planResultId);
                return false;
            }
            
            planResultsCache.invalidate(planResultId);
            log.info("Successfully removed plan result for ID: {}", planResultId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to remove plan result for ID: {}", planResultId, e);
            return false;
        }
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        try {
            return planResultsCache.stats().toString();
        } catch (Exception e) {
            log.error("Failed to get cache statistics", e);
            return "Cache statistics unavailable";
        }
    }

    /**
     * 清空所有缓存的计划结果
     */
    public void clearAllPlanResults() {
        try {
            planResultsCache.invalidateAll();
            log.info("Successfully cleared all plan results from cache");
        } catch (Exception e) {
            log.error("Failed to clear all plan results from cache", e);
        }
    }
}
