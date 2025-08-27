package com.pkfare.trip.scale.util;

import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 缓存键生成工具类
 * 
 * @author Trip Scale Team
 */
@Slf4j
public class CacheKeyUtil {

    private static final String SEPARATOR = "|";
    
    /**
     * 为航班日期请求生成缓存键
     * 
     * @param request 航班日期请求
     * @return 缓存键
     */
    public static String generateFlightDatesKey(FlightDatesRequest request) {
        if (request == null) {
            return "null";
        }
        
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("flight_dates").append(SEPARATOR);
        keyBuilder.append(StringUtils.defaultString(request.getOrigin(), "")).append(SEPARATOR);
        keyBuilder.append(StringUtils.defaultString(request.getDestination(), "")).append(SEPARATOR);
        keyBuilder.append(StringUtils.defaultString(request.getDepartureDate(), "")).append(SEPARATOR);
        keyBuilder.append(Objects.toString(request.getOneWay(), "")).append(SEPARATOR);
        keyBuilder.append(StringUtils.defaultString(request.getDuration(), "")).append(SEPARATOR);
        keyBuilder.append(Objects.toString(request.getNonStop(), "")).append(SEPARATOR);
        keyBuilder.append(request.getMaxPrice());
        
        String rawKey = keyBuilder.toString();
        
        // 使用MD5生成固定长度的键，避免键过长
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(rawKey.getBytes());
            StringBuilder hashBuilder = new StringBuilder();
            for (byte b : hashBytes) {
                hashBuilder.append(String.format("%02x", b));
            }
            String hashedKey = "fd_" + hashBuilder.toString();
            
            log.debug("Generated cache key: {} for request: {}", hashedKey, rawKey);
            return hashedKey;
            
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 algorithm not available, using raw key", e);
            return rawKey.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
    }
    
    /**
     * 验证缓存键是否有效
     * 
     * @param key 缓存键
     * @return 是否有效
     */
    public static boolean isValidCacheKey(String key) {
        return StringUtils.isNotBlank(key) && !key.equals("null");
    }
}
