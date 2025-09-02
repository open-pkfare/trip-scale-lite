package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.Activity;
import com.github.benmanes.caffeine.cache.Cache;
import com.pkfare.trip.scale.api.amadeus.activities.AmadeusActivitiesSearchApi;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import com.pkfare.trip.scale.exception.ExternalApiException;
import com.pkfare.trip.scale.util.CacheKeyUtil;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Amadeus活动服务
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class AmadeusActivityService {
    
    private final AmadeusActivitiesSearchApi activitiesSearchApi;
    private final Cache<String, Object> activitiesCache;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusActivityService(@Qualifier("activitiesCache") Cache<String, Object> activitiesCache) {
        this.activitiesSearchApi = new AmadeusActivitiesSearchApi();
        this.activitiesCache = activitiesCache;
        log.info("AmadeusActivityService initialized with activities cache");
    }
    
    /**
     * 搜索活动 - 带缓存功能
     * 优先从缓存获取，缓存未命中时调用API并缓存结果
     * 
     * @param request 活动搜索请求
     * @return 活动数组
     */
    public List<ActivityDto>  searchActivities(ActivitiesSearchRequest request) {
        log.info("Searching activities with request: {}", request);
        
        // 生成缓存键
        String cacheKey = CacheKeyUtil.generateActivitiesKey(request);
        if (!CacheKeyUtil.isValidCacheKey(cacheKey)) {
            log.warn("Invalid cache key generated, proceeding without cache");
            return searchActivitiesWithoutCache(request);
        }
        
        // 1. 先尝试从缓存获取
        List<ActivityDto>  cachedResult = getCachedActivities(cacheKey);
        if (cachedResult != null) {
            log.info("Cache hit! Returning {} cached activities for key: {}", 
                cachedResult.size(), cacheKey);
            logActivitiesCacheStats();
            return cachedResult;
        }
        
        // 2. 缓存未命中，调用API获取数据
        log.info("Cache miss for key: {}, calling API", cacheKey);
        List<ActivityDto>  apiResult = searchActivitiesWithoutCache(request);
        
        // 3. 将API结果缓存起来
        if (apiResult != null && apiResult.size() > 0) {
            cacheActivitiesResult(cacheKey, apiResult);
            log.info("API result cached successfully - {} activities for key: {}", 
                apiResult.size(), cacheKey);
        } else {
            log.warn("API returned empty result, not caching for key: {}", cacheKey);
        }
        
        logActivitiesCacheStats();
        return apiResult;
    }
    
    /**
     * 从缓存获取活动数据
     * 
     * @param cacheKey 缓存键
     * @return 缓存的活动数据，如果不存在返回null
     */
    private List<ActivityDto>  getCachedActivities(String cacheKey) {
        try {
            Object cached = activitiesCache.getIfPresent(cacheKey);
            if (cached instanceof Activity[]) {
                List<ActivityDto>  cachedResult = (List<ActivityDto> ) cached;
                log.debug("Cache hit for key: {}, found {} results", cacheKey, cachedResult.size());
                return cachedResult;
            } else if (cached != null) {
                log.warn("Invalid cached object type: {}, removing from cache", cached.getClass());
                activitiesCache.invalidate(cacheKey);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve from cache for key: {}", cacheKey, e);
        }
        
        log.debug("Cache miss for key: {}", cacheKey);
        return null;
    }
    
    /**
     * 将活动搜索结果缓存
     * 
     * @param cacheKey 缓存键
     * @param result API查询结果
     */
    private void cacheActivitiesResult(String cacheKey, List<ActivityDto>  result) {
        try {
            activitiesCache.put(cacheKey, result);
            log.debug("Cached {} activities for key: {}", result.size(), cacheKey);
        } catch (Exception e) {
            log.error("Failed to cache result for key: {}", cacheKey, e);
        }
    }
    
    /**
     * 不使用缓存直接搜索活动
     * 
     * @param request 活动搜索请求
     * @return 活动数组
     */
    private List<ActivityDto>  searchActivitiesWithoutCache(ActivitiesSearchRequest request) {
        return retryApiCall(() -> {
            try {
                List<ActivityDto> result = activitiesSearchApi.searchActivities(request);
                log.debug("API call completed, found {} results", result != null ? result.size() : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search activities via API", e);
                throw new ExternalApiException("AMADEUS_ACTIVITIES_ERROR", 
                    "Failed to search activities: " + e.getMessage(), 500, "AmadeusActivitiesSearchApi", e);
            }
        }, MAX_RETRY_ATTEMPTS);
    }
    
    /**
     * 记录活动搜索缓存统计信息
     */
    private void logActivitiesCacheStats() {
        try {
            var stats = activitiesCache.stats();
            log.debug("Activities cache stats - size: {}, hitRate: {:.2f}%, evictionCount: {}", 
                activitiesCache.estimatedSize(), 
                stats.hitRate() * 100, 
                stats.evictionCount());
        } catch (Exception e) {
            log.debug("Failed to log activities cache stats", e);
        }
    }
    
    /**
     * 清除活动搜索缓存
     */
    public void clearActivitiesCache() {
        activitiesCache.invalidateAll();
        log.info("Activities cache cleared");
    }
    
    /**
     * 获取活动搜索缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getActivitiesCacheStats() {
        try {
            var stats = activitiesCache.stats();
            return String.format("Activities Cache Stats - Size: %d, HitRate: %.2f%%, MissRate: %.2f%%, EvictionCount: %d", 
                activitiesCache.estimatedSize(),
                stats.hitRate() * 100,
                stats.missRate() * 100,
                stats.evictionCount());
        } catch (Exception e) {
            return "Activities cache stats unavailable: " + e.getMessage();
        }
    }
    
    /**
     * 处理API异常
     * 
     * @param e 异常
     */
    private void handleApiException(Exception e) {
        log.error("Amadeus API call failed", e);
        if (e instanceof ExternalApiException) {
            throw (ExternalApiException) e;
        }
        throw new ExternalApiException("AMADEUS_API_ERROR", 
            "Amadeus API call failed: " + e.getMessage(), 500, "AmadeusAPI", e);
    }
    
    /**
     * 重试API调用
     * 
     * @param supplier API调用供应商
     * @param maxAttempts 最大重试次数
     * @param <T> 返回类型
     * @return API调用结果
     */
    private <T> T retryApiCall(Supplier<T> supplier, int maxAttempts) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("API call attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        handleApiException(lastException);
        return null; // This line should never be reached
    }
}
