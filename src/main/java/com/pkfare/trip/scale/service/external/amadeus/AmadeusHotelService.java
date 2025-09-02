package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.Hotel;
import com.amadeus.resources.HotelOfferSearch;
import com.github.benmanes.caffeine.cache.Cache;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.AmadeusHotelOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import com.pkfare.trip.scale.exception.ExternalApiException;
import com.pkfare.trip.scale.util.CacheKeyUtil;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Amadeus酒店服务
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class AmadeusHotelService {
    
    private final AmadeusSearchHotelsByCityAPI hotelsByCityAPI;
    private final AmadeusHotelOffersSearchAPI hotelOffersAPI;
    private final Cache<String, Object> hotelOffersCache;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusHotelService(@Qualifier("hotelOffersCache") Cache<String, Object> hotelOffersCache) {
        this.hotelsByCityAPI = new AmadeusSearchHotelsByCityAPI();
        this.hotelOffersAPI = new AmadeusHotelOffersSearchAPI();
        this.hotelOffersCache = hotelOffersCache;
        log.info("AmadeusHotelService initialized with hotel offers cache");
    }
    
    /**
     * 根据城市搜索酒店
     * 
     * @param request 城市酒店搜索请求
     * @return 酒店数组
     */
    public Hotel[] searchHotelsByCity(QueryHotelByCityRequest request) {
        log.info("Searching hotels by city with request: {}", request);
        
        return retryApiCall(() -> {
            try {
                Hotel[] result = hotelsByCityAPI.queryHotelByCity(request);
                log.info("Hotels by city search completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search hotels by city", e);
                throw new ExternalApiException("AMADEUS_HOTELS_BY_CITY_ERROR", 
                    "Failed to search hotels by city: " + e.getMessage(), 500, "AmadeusSearchHotelsByCityAPI", e);
            }
        }, MAX_RETRY_ATTEMPTS);
    }
    
    /**
     * 搜索酒店报价 - 带缓存功能
     * 优先从缓存获取，缓存未命中时调用API并缓存结果
     * 
     * @param request 酒店报价搜索请求
     * @return 酒店报价数组
     */
    public List<HotelOfferDto> searchHotelOffers(HotelOffersSearchRequest request) {
        log.info("Searching hotel offers with request: {}", request);
        
        // 生成缓存键
        String cacheKey = CacheKeyUtil.generateHotelOffersKey(request);
        if (!CacheKeyUtil.isValidCacheKey(cacheKey)) {
            log.warn("Invalid cache key generated, proceeding without cache");
            return searchHotelOffersWithoutCache(request);
        }
        
        // 1. 先尝试从缓存获取
        List<HotelOfferDto> cachedResult = getCachedHotelOffers(cacheKey);
        if (cachedResult != null) {
            log.info("Cache hit! Returning {} cached hotel offers for key: {}", 
                cachedResult.size(), cacheKey);
            logHotelOffersCacheStats();
            return cachedResult;
        }
        
        // 2. 缓存未命中，调用API获取数据
        log.info("Cache miss for key: {}, calling API", cacheKey);
        List<HotelOfferDto> apiResult = searchHotelOffersWithoutCache(request);
        
        // 3. 将API结果缓存起来
        if (apiResult != null && apiResult.size() > 0) {
            cacheHotelOffersResult(cacheKey, apiResult);
            log.info("API result cached successfully - {} hotel offers for key: {}", 
                apiResult.size(), cacheKey);
        } else {
            log.warn("API returned empty result, not caching for key: {}", cacheKey);
        }
        
        logHotelOffersCacheStats();
        return apiResult;
    }
    
    /**
     * 从缓存获取酒店报价
     * 
     * @param cacheKey 缓存键
     * @return 缓存的酒店报价，如果不存在返回null
     */
    private List<HotelOfferDto> getCachedHotelOffers(String cacheKey) {
        try {
            Object cached = hotelOffersCache.getIfPresent(cacheKey);
            if (cached instanceof HotelOfferSearch[]) {
                List<HotelOfferDto> cachedResult = (List<HotelOfferDto>) cached;
                log.debug("Cache hit for key: {}, found {} results", cacheKey, cachedResult.size());
                return cachedResult;
            } else if (cached != null) {
                log.warn("Invalid cached object type: {}, removing from cache", cached.getClass());
                hotelOffersCache.invalidate(cacheKey);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve from cache for key: {}", cacheKey, e);
        }
        
        log.debug("Cache miss for key: {}", cacheKey);
        return null;
    }
    
    /**
     * 将酒店报价结果缓存
     * 
     * @param cacheKey 缓存键
     * @param result API查询结果
     */
    private void cacheHotelOffersResult(String cacheKey, List<HotelOfferDto> result) {
        try {
            hotelOffersCache.put(cacheKey, result);
            log.debug("Cached {} hotel offers for key: {}", result.size(), cacheKey);
        } catch (Exception e) {
            log.error("Failed to cache result for key: {}", cacheKey, e);
        }
    }
    
    /**
     * 不使用缓存直接搜索酒店报价
     * 
     * @param request 酒店报价搜索请求
     * @return 酒店报价数组
     */
    private List<HotelOfferDto> searchHotelOffersWithoutCache(HotelOffersSearchRequest request) {
        return retryApiCall(() -> {
            try {
                List<HotelOfferDto> result = hotelOffersAPI.hotelOffersSearch(request);
                log.debug("API call completed, found {} results", result != null ? result.size() : 0);
                log.info("Returning hotels:{}", result );
                return result;
            } catch (Exception e) {
                log.error("Failed to search hotel offers via API", e);
                throw new ExternalApiException("AMADEUS_HOTEL_OFFERS_ERROR", 
                    "Failed to search hotel offers: " + e.getMessage(), 500, "AmadeusHotelOffersSearchAPI", e);
            }
        }, MAX_RETRY_ATTEMPTS);
    }
    
    /**
     * 记录酒店报价缓存统计信息
     */
    private void logHotelOffersCacheStats() {
        try {
            var stats = hotelOffersCache.stats();
            log.debug("Hotel offers cache stats - size: {}, hitRate: {:.2f}%, evictionCount: {}", 
                hotelOffersCache.estimatedSize(), 
                stats.hitRate() * 100, 
                stats.evictionCount());
        } catch (Exception e) {
            log.debug("Failed to log hotel offers cache stats", e);
        }
    }
    
    /**
     * 清除酒店报价缓存
     */
    public void clearHotelOffersCache() {
        hotelOffersCache.invalidateAll();
        log.info("Hotel offers cache cleared");
    }
    
    /**
     * 获取酒店报价缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getHotelOffersCacheStats() {
        try {
            var stats = hotelOffersCache.stats();
            return String.format("Hotel Offers Cache Stats - Size: %d, HitRate: %.2f%%, MissRate: %.2f%%, EvictionCount: %d", 
                hotelOffersCache.estimatedSize(),
                stats.hitRate() * 100,
                stats.missRate() * 100,
                stats.evictionCount());
        } catch (Exception e) {
            return "Hotel offers cache stats unavailable: " + e.getMessage();
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
