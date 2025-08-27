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
        
        // 使用并行流处理多个城市查询，提高性能
        List<CityLocationInfo> locationInfos = cityList.parallelStream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct() // 去重
                .map(this::searchSingleCityLocation)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.info("Successfully found locations for {} out of {} cities", locationInfos.size(), cityList.size());
        return locationInfos;
    }

    /**
     * 查询单个城市的位置信息
     * 
     * @param cityCode 城市代码（IATA机场代码或城市名称）
     * @return 城市位置信息，查询失败返回null
     */
    private CityLocationInfo searchSingleCityLocation(String city) {
        // 先检查缓存
        CityLocationInfo cachedLocation = locationCache.get(city);
        if (cachedLocation != null) {
            log.debug("Found cached location for city: {}", city);
            return cachedLocation;
        }
        
        try {
            log.debug("Searching location for city: {}", city);
            
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
                
                log.info("Successfully found location for city {}: lat={}, lng={}",
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
}
