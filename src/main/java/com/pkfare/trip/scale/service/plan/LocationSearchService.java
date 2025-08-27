package com.pkfare.trip.scale.service.plan;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.plan.service.response.CityLocationInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 位置搜索服务
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class LocationSearchService {

    private final GeoApiContext geoApiContext;
    
    // 缓存城市位置信息，避免重复查询
    private final ConcurrentHashMap<String, CityLocationInfo> locationCache = new ConcurrentHashMap<>();

    public LocationSearchService() {
        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(GoogleConfig.GOOGLE_API_KEY)
                .build();
    }

    /**
     * 根据城市代码列表查询城市的经纬度信息
     * 优先从缓存获取，只对缓存中没有的城市进行API查询
     * 
     * @param cityList 城市代码列表（IATA机场代码或城市名称）
     * @return 城市位置信息列表
     */
    public List<CityLocationInfo> searchCityLocations(List<String> cityList) {
        if (cityList == null) {
            log.warn("City list is null, returning empty result");
            return new ArrayList<>();
        }
        
        log.info("Searching city locations for {} cities: {}", cityList.size(), cityList);
        
        if (cityList.isEmpty()) {
            log.warn("City list is empty, returning empty result");
            return new ArrayList<>();
        }
        
        // 1. 预处理城市列表：去空、去重、trim
        List<String> processedCities = cityList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        
        log.debug("Processed city list: {} unique cities", processedCities.size());
        
        // 2. 分离缓存命中和未命中的城市
        List<CityLocationInfo> cachedResults = new ArrayList<>();
        List<String> citiesToQuery = new ArrayList<>();
        
        for (String city : processedCities) {
            CityLocationInfo cachedLocation = locationCache.get(city);
            if (cachedLocation != null) {
                cachedResults.add(cachedLocation);
                log.debug("Cache hit for city: {}", city);
            } else {
                citiesToQuery.add(city);
                log.debug("Cache miss for city: {}", city);
            }
        }
        
        log.info("Cache statistics - hits: {}, misses: {}, total: {}", 
            cachedResults.size(), citiesToQuery.size(), processedCities.size());
        
        // 3. 对缓存中没有的城市进行API查询
        List<CityLocationInfo> apiResults = new ArrayList<>();
        if (!citiesToQuery.isEmpty()) {
            log.info("Querying {} cities from API: {}", citiesToQuery.size(), citiesToQuery);
            
            // 使用并行流查询未缓存的城市
            apiResults = citiesToQuery.parallelStream()
                    .map(this::searchSingleCityLocationFromApi)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            log.info("API query completed, found {} out of {} cities", apiResults.size(), citiesToQuery.size());
        }
        
        // 4. 合并缓存结果和API查询结果
        List<CityLocationInfo> allResults = new ArrayList<>();
        allResults.addAll(cachedResults);
        allResults.addAll(apiResults);
        
        log.info("Successfully found locations for {} out of {} cities (cached: {}, api: {})", 
            allResults.size(), cityList.size(), cachedResults.size(), apiResults.size());
        
        return allResults;
    }

    /**
     * 从API查询单个城市的位置信息并缓存结果
     * 
     * @param city 城市代码（IATA机场代码或城市名称）
     * @return 城市位置信息，查询失败返回null
     */
    private CityLocationInfo searchSingleCityLocationFromApi(String city) {
        try {
            log.debug("Searching location for city from API: {}", city);
            
            // 调用Google Maps Geocoding API
            GeocodingResult[] results = GeocodingApi.geocode(geoApiContext, city).await();
            
            if (results != null && results.length > 0) {
                GeocodingResult result = results[0];
                LatLng location = result.geometry.location;
                
                // 创建城市位置信息对象
                CityLocationInfo cityLocationInfo = new CityLocationInfo();
                cityLocationInfo.setCityName(city);
                cityLocationInfo.setLatitude(location.lat);
                cityLocationInfo.setLongitude(location.lng);
                
                // 缓存结果
                locationCache.put(city, cityLocationInfo);
                
                log.info("Successfully found and cached location for city {}: lat={}, lng={}",
                    city, location.lat, location.lng);
                
                return cityLocationInfo;
            } else {
                log.warn("No geocoding results found for city: {}", city);
            }
            
        } catch (Exception e) {
            log.error("Failed to search location for city: {}", city, e);
        }
        
        return null;
    }

    /**
     * 清除位置缓存
     */
    public void clearLocationCache() {
        locationCache.clear();
        log.info("Location cache cleared");
    }

    /**
     * 获取缓存大小
     * 
     * @return 缓存中的城市数量
     */
    public int getCacheSize() {
        return locationCache.size();
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        return String.format("Location Cache Stats - Size: %d cities", locationCache.size());
    }
    
    /**
     * 检查指定城市是否已缓存
     * 
     * @param city 城市名称
     * @return 是否已缓存
     */
    public boolean isCached(String city) {
        return StringUtils.isNotBlank(city) && locationCache.containsKey(city.trim());
    }
    
    /**
     * 获取缓存中的城市列表
     * 
     * @return 已缓存的城市名称列表
     */
    public List<String> getCachedCities() {
        return new ArrayList<>(locationCache.keySet());
    }
    
    /**
     * 手动添加城市位置信息到缓存
     * 
     * @param city 城市名称
     * @param locationInfo 位置信息
     */
    public void putToCache(String city, CityLocationInfo locationInfo) {
        if (StringUtils.isNotBlank(city) && locationInfo != null) {
            locationCache.put(city.trim(), locationInfo);
            log.debug("Manually added city {} to cache", city);
        }
    }
    
    /**
     * 从缓存中移除指定城市
     * 
     * @param city 城市名称
     * @return 被移除的位置信息，如果不存在返回null
     */
    public CityLocationInfo removeFromCache(String city) {
        if (StringUtils.isNotBlank(city)) {
            CityLocationInfo removed = locationCache.remove(city.trim());
            if (removed != null) {
                log.debug("Removed city {} from cache", city);
            }
            return removed;
        }
        return null;
    }
}
