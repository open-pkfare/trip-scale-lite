package com.pkfare.trip.scale.service.external.amadeus;

import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.util.CacheKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AmadeusFlightService 缓存功能单元测试
 * 不依赖Spring上下文的纯单元测试
 * 
 * @author Trip Scale Team
 */
@Slf4j
public class AmadeusFlightServiceCacheUnitTest {

    @Test
    public void testFlightDatesKeyGeneration() {
        log.info("=== Testing Flight Dates Cache Key Generation ===");
        
        FlightDatesRequest request1 = createFlightDatesRequest("SZX", "FCO", "2025-10-01", true, "7", true, 1000);
        FlightDatesRequest request2 = createFlightDatesRequest("SZX", "FCO", "2025-10-01", true, "7", true, 1000);
        FlightDatesRequest request3 = createFlightDatesRequest("SZX", "ORY", "2025-10-01", true, "7", true, 1000);
        
        String key1 = CacheKeyUtil.generateFlightDatesKey(request1);
        String key2 = CacheKeyUtil.generateFlightDatesKey(request2);
        String key3 = CacheKeyUtil.generateFlightDatesKey(request3);
        
        log.info("Key1: {}", key1);
        log.info("Key2: {}", key2);
        log.info("Key3: {}", key3);
        
        // 相同参数应该生成相同的键
        assertEquals(key1, key2, "Same parameters should generate same cache key");
        
        // 不同参数应该生成不同的键
        assertNotEquals(key1, key3, "Different parameters should generate different cache keys");
        
        // 键应该以fd_开头
        assertTrue(key1.startsWith("fd_"), "Flight dates key should start with fd_");
        
        log.info("Flight dates key generation test passed");
    }

    @Test
    public void testFlightOffersKeyGeneration() {
        log.info("=== Testing Flight Offers Cache Key Generation ===");
        
        FlightOffersSearchRequest request1 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 2, 0, 0, true, "EUR", 1000, 10);
        FlightOffersSearchRequest request2 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 2, 0, 0, true, "EUR", 1000, 10);
        FlightOffersSearchRequest request3 = createFlightOffersRequest("SZX", "ORY", "2025-10-01", "2025-10-08", 2, 0, 0, true, "EUR", 1000, 10);
        
        String key1 = CacheKeyUtil.generateFlightOffersKey(request1);
        String key2 = CacheKeyUtil.generateFlightOffersKey(request2);
        String key3 = CacheKeyUtil.generateFlightOffersKey(request3);
        
        log.info("Key1: {}", key1);
        log.info("Key2: {}", key2);
        log.info("Key3: {}", key3);
        
        // 相同参数应该生成相同的键
        assertEquals(key1, key2, "Same parameters should generate same cache key");
        
        // 不同参数应该生成不同的键
        assertNotEquals(key1, key3, "Different parameters should generate different cache keys");
        
        // 键应该以fo_开头
        assertTrue(key1.startsWith("fo_"), "Flight offers key should start with fo_");
        
        log.info("Flight offers key generation test passed");
    }

    @Test
    public void testCacheKeyValidation() {
        log.info("=== Testing Cache Key Validation ===");
        
        // 测试有效键
        assertTrue(CacheKeyUtil.isValidCacheKey("fd_abc123"), "Valid flight dates key should pass validation");
        assertTrue(CacheKeyUtil.isValidCacheKey("fo_def456"), "Valid flight offers key should pass validation");
        
        // 测试无效键
        assertFalse(CacheKeyUtil.isValidCacheKey(null), "Null key should fail validation");
        assertFalse(CacheKeyUtil.isValidCacheKey(""), "Empty key should fail validation");
        assertFalse(CacheKeyUtil.isValidCacheKey("   "), "Blank key should fail validation");
        assertFalse(CacheKeyUtil.isValidCacheKey("null"), "Null string should fail validation");
        
        log.info("Cache key validation test passed");
    }

    @Test
    public void testFlightOffersKeyWithDifferentParameters() {
        log.info("=== Testing Flight Offers Key with Different Parameters ===");
        
        // 测试不同成人数量
        FlightOffersSearchRequest request1 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 1, 0, 0, true, "EUR", 1000, 10);
        FlightOffersSearchRequest request2 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 2, 0, 0, true, "EUR", 1000, 10);
        
        String key1 = CacheKeyUtil.generateFlightOffersKey(request1);
        String key2 = CacheKeyUtil.generateFlightOffersKey(request2);
        
        assertNotEquals(key1, key2, "Different adults count should generate different keys");
        
        // 测试不同货币
        FlightOffersSearchRequest request3 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 2, 0, 0, true, "USD", 1000, 10);
        String key3 = CacheKeyUtil.generateFlightOffersKey(request3);
        
        assertNotEquals(key2, key3, "Different currency should generate different keys");
        
        // 测试不同价格限制
        FlightOffersSearchRequest request4 = createFlightOffersRequest("SZX", "FCO", "2025-10-01", "2025-10-08", 2, 0, 0, true, "EUR", 2000, 10);
        String key4 = CacheKeyUtil.generateFlightOffersKey(request4);
        
        assertNotEquals(key2, key4, "Different max price should generate different keys");
        
        log.info("Flight offers key parameter variation test passed");
    }

    @Test
    public void testNullRequestHandling() {
        log.info("=== Testing Null Request Handling ===");
        
        String flightDatesKey = CacheKeyUtil.generateFlightDatesKey(null);
        String flightOffersKey = CacheKeyUtil.generateFlightOffersKey(null);
        
        assertEquals("null", flightDatesKey, "Null flight dates request should return 'null'");
        assertEquals("null", flightOffersKey, "Null flight offers request should return 'null'");
        
        assertFalse(CacheKeyUtil.isValidCacheKey(flightDatesKey), "Null key should be invalid");
        assertFalse(CacheKeyUtil.isValidCacheKey(flightOffersKey), "Null key should be invalid");
        
        log.info("Null request handling test passed");
    }

    /**
     * 创建测试用的航班日期请求
     */
    private FlightDatesRequest createFlightDatesRequest(String origin, String destination, String departureDate, 
                                                       Boolean oneWay, String duration, Boolean nonStop, int maxPrice) {
        FlightDatesRequest request = new FlightDatesRequest();
        request.setOrigin(origin);
        request.setDestination(destination);
        request.setDepartureDate(departureDate);
        request.setOneWay(oneWay);
        request.setDuration(duration);
        request.setNonStop(nonStop);
        request.setMaxPrice(maxPrice);
        return request;
    }

    /**
     * 创建测试用的航班报价请求
     */
    private FlightOffersSearchRequest createFlightOffersRequest(String origin, String destination, 
                                                               String departureDate, String returnDate,
                                                               int adults, int children, int infants,
                                                               Boolean nonStop, String currency, 
                                                               int maxPrice, Integer max) {
        FlightOffersSearchRequest request = new FlightOffersSearchRequest();
        request.setOrigin(origin);
        request.setDestination(destination);
        request.setDepartureDate(departureDate);
        request.setReturnDate(returnDate);
        request.setAdults(adults);
        request.setChildren(children);
        request.setInfants(infants);
        request.setNonStop(nonStop);
        request.setCurrency(currency);
        request.setMaxPrice(maxPrice);
        request.setMax(max);
        return request;
    }
}
