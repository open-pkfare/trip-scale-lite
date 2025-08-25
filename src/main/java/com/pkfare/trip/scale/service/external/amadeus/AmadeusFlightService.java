package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.FlightDate;

import com.amadeus.resources.FlightOfferSearch;
import com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;

import com.pkfare.trip.scale.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
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
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusFlightService() {
        this.flightDatesAPI = new AmadeusFlightDatesAPI();
        this.flightOffersAPI = new AmadeusFlightOffersSearchAPI();
    }
    
    /**
     * 搜索航班日期
     * 
     * @param request 航班日期搜索请求
     * @return 航班日期数组
     */
    public FlightDate[] searchFlightDates(FlightDatesRequest request) {
        log.info("Searching flight dates with request: {}", request);
        
        return retryApiCall(() -> {
            try {
                FlightDate[] result = flightDatesAPI.flightDates(request);
                log.info("Flight dates search completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search flight dates", e);
                throw new ExternalApiException("AMADEUS_FLIGHT_DATES_ERROR", 
                    "Failed to search flight dates: " + e.getMessage(), 500, "AmadeusFlightDatesAPI", e);
            }
        }, MAX_RETRY_ATTEMPTS);
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
