package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.Hotel;
import com.amadeus.resources.HotelOfferSearch;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.AmadeusHotelOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
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
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public AmadeusHotelService() {
        this.hotelsByCityAPI = new AmadeusSearchHotelsByCityAPI();
        this.hotelOffersAPI = new AmadeusHotelOffersSearchAPI();
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
     * 搜索酒店报价
     * 
     * @param request 酒店报价搜索请求
     * @return 酒店报价数组
     */
    public HotelOfferSearch[] searchHotelOffers(HotelOffersSearchRequest request) {
        log.info("Searching hotel offers with request: {}", request);
        
        return retryApiCall(() -> {
            try {
                HotelOfferSearch[] result = hotelOffersAPI.hotelOffersSearch(request);
                log.info("Hotel offers search completed, found {} results", result != null ? result.length : 0);
                return result;
            } catch (Exception e) {
                log.error("Failed to search hotel offers", e);
                throw new ExternalApiException("AMADEUS_HOTEL_OFFERS_ERROR", 
                    "Failed to search hotel offers: " + e.getMessage(), 500, "AmadeusHotelOffersSearchAPI", e);
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
