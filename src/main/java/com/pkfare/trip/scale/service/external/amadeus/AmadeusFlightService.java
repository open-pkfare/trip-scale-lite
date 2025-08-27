package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.FlightDate;
import com.amadeus.resources.FlightOfferSearch;
import com.github.benmanes.caffeine.cache.Cache;
import com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.exception.ExternalApiException;
import com.pkfare.trip.scale.util.CacheKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Amadeus航班服务
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class AmadeusFlightService {
    
    private final AmadeusFlightDatesAPI flightDatesAPI;
    private final AmadeusFlightOffersSearchAPI flightOffersAPI;
    private final Cache<String, Object> flightDatesCache;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusFlightService(@Qualifier("flightDatesCache") Cache<String, Object> flightDatesCache) {
        this.flightDatesAPI = new AmadeusFlightDatesAPI();
        this.flightOffersAPI = new AmadeusFlightOffersSearchAPI();
        this.flightDatesCache = flightDatesCache;
        log.info("AmadeusFlightService initialized with cache");
    }
    
    /**
     * 搜索航班日期 - 带缓存功能
     * 优先从缓存获取，缓存未命中时调用API并缓存结果
     * 
     * @param request 航班日期搜索请求
     * @return 航班日期数组
     */
    public FlightDate[] searchFlightDates(FlightDatesRequest request) {
        log.info("Searching flight dates with request: {}", request);
        
        // 生成缓存键
        String cacheKey = CacheKeyUtil.generateFlightDatesKey(request);
        if (!CacheKeyUtil.isValidCacheKey(cacheKey)) {
            log.warn("Invalid cache key generated, proceeding without cache");
            return searchFlightDatesWithoutCache(request);
        }
        
        // 1. 先尝试从缓存获取
        FlightDate[] cachedResult = getCachedFlightDates(cacheKey);
        if (cachedResult != null) {
            log.info("Cache hit! Returning {} cached flight dates for key: {}", 
                cachedResult.length, cacheKey);
            logCacheStats();
            return cachedResult;
        }
        
        // 2. 缓存未命中，调用API获取数据
        log.info("Cache miss for key: {}, calling API", cacheKey);
        FlightDate[] apiResult = searchFlightDatesWithoutCache(request);
        
        // 3. 将API结果缓存起来
        if (apiResult != null && apiResult.length > 0) {
            cacheFlightDatesResult(cacheKey, apiResult);
            log.info("API result cached successfully - {} flight dates for key: {}", 
                apiResult.length, cacheKey);
        } else {
            log.warn("API returned empty result, not caching for key: {}", cacheKey);
        }
        
        logCacheStats();
        return apiResult;
    }
    
    /**
     * 从缓存获取航班日期
     * 
     * @param cacheKey 缓存键
     * @return 缓存的航班日期，如果不存在返回null
     */
    private FlightDate[] getCachedFlightDates(String cacheKey) {
        try {
            Object cached = flightDatesCache.getIfPresent(cacheKey);
            if (cached instanceof FlightDate[]) {
                FlightDate[] cachedResult = (FlightDate[]) cached;
                log.debug("Cache hit for key: {}, found {} results", cacheKey, cachedResult.length);
                return cachedResult;
            } else if (cached != null) {
                log.warn("Invalid cached object type: {}, removing from cache", cached.getClass());
                flightDatesCache.invalidate(cacheKey);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve from cache for key: {}", cacheKey, e);
        }
        
        log.debug("Cache miss for key: {}", cacheKey);
        return null;
    }
    
    /**
     * 将航班日期结果缓存
     * 
     * @param cacheKey 缓存键
     * @param result API查询结果
     */
    private void cacheFlightDatesResult(String cacheKey, FlightDate[] result) {
        try {
            flightDatesCache.put(cacheKey, result);
            log.debug("Cached {} flight dates for key: {}", result.length, cacheKey);
        } catch (Exception e) {
            log.error("Failed to cache result for key: {}", cacheKey, e);
        }
    }
    
    /**
     * 不使用缓存直接搜索航班日期
     * 
     * @param request 航班日期搜索请求
     * @return 航班日期数组
     */
    private FlightDate[] searchFlightDatesWithoutCache(FlightDatesRequest request) {
        return retryApiCall(() -> {
            try {
                FlightDate[] result = flightDatesAPI.flightDates(request);
                log.debug("API call completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search flight dates via API", e);
                throw new ExternalApiException("AMADEUS_FLIGHT_DATES_ERROR", 
                    "Failed to search flight dates: " + e.getMessage(), 500, "AmadeusFlightDatesAPI", e);
            }
        }, MAX_RETRY_ATTEMPTS);
    }
    
    /**
     * 记录缓存统计信息
     */
    private void logCacheStats() {
        try {
            var stats = flightDatesCache.stats();
            log.debug("Cache stats - size: {}, hitRate: {:.2f}%, evictionCount: {}", 
                flightDatesCache.estimatedSize(), 
                stats.hitRate() * 100, 
                stats.evictionCount());
        } catch (Exception e) {
            log.debug("Failed to log cache stats", e);
        }
    }
    
    /**
     * 清除航班日期缓存
     */
    public void clearFlightDatesCache() {
        flightDatesCache.invalidateAll();
        log.info("Flight dates cache cleared");
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        try {
            var stats = flightDatesCache.stats();
            return String.format("Cache Stats - Size: %d, HitRate: %.2f%%, MissRate: %.2f%%, EvictionCount: %d", 
                flightDatesCache.estimatedSize(),
                stats.hitRate() * 100,
                stats.missRate() * 100,
                stats.evictionCount());
        } catch (Exception e) {
            return "Cache stats unavailable: " + e.getMessage();
        }
    }
    
    /**
     * 搜索航班报价
     * 
     * @param request 航班报价搜索请求
     * @return 航班报价数组
     */
    public FlightOfferSearch[] searchFlightOffers(FlightOffersSearchRequest request) {
        log.info("Searching flight offers with request: {}", request);
        
        return retryApiCall(() -> {
            try {
                FlightOfferSearch[] result = flightOffersAPI.flightOffersSearch(request);
                log.info("Flight offers search completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search flight offers", e);
                throw new ExternalApiException("AMADEUS_FLIGHT_OFFERS_ERROR", 
                    "Failed to search flight offers: " + e.getMessage(), 500, "AmadeusFlightOffersSearchAPI", e);
            }
        }, MAX_RETRY_ATTEMPTS);
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
