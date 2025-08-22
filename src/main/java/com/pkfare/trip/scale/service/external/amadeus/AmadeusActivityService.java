package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.Activity;
import com.pkfare.trip.scale.api.amadeus.activities.AmadeusActivitiesSearchApi;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
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
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusActivityService() {
        this.activitiesSearchApi = new AmadeusActivitiesSearchApi();
    }
    
    /**
     * 搜索活动
     * 
     * @param request 活动搜索请求
     * @return 活动数组
     */
    public Activity[] searchActivities(ActivitiesSearchRequest request) {
        log.info("Searching activities with request: {}", request);
        
        return retryApiCall(() -> {
            try {
                Activity[] result = activitiesSearchApi.searchActivities(request);
                log.info("Activities search completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search activities", e);
                throw new ExternalApiException("AMADEUS_ACTIVITIES_ERROR", 
                    "Failed to search activities: " + e.getMessage(), 500, "AmadeusActivitiesSearchApi", e);
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
