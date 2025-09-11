package com.pkfare.trip.scale.api.amadeus.airportlocations;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import com.pkfare.trip.scale.api.amadeus.airportlocations.request.FlightAirportLocationSearchRequest;
import com.pkfare.trip.scale.api.amadeus.airportlocations.response.LocationDto;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AmadeusFlightAirportLocationSearchAPI {

  @Value("${amadeus.locations.mock.enabled:true}")
  private boolean mockEnabled;
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<LocationDto> queryFlightLocation(FlightAirportLocationSearchRequest request) {
    log.info("FlightAirportLocationSearchRequest:{}",request);
    if(needMock(request)){
      return mockApiResponse(request);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("subType", request.getSubType())
        .and("keyword", request.getKeyword());

    try {
      Location[] locations = amadeus.referenceData.locations.get(params);
      if (locations == null || locations.length == 0) {
        log.error("call AmadeusFlightAirportLocationSearchAPI return empty，resonse:{} ", locations);
        return Lists.newArrayList();
      }
      if (locations[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightAirportLocationSearchAPI failed，resonse：{}", locations[0].getResponse());
        throw new AmadeusApiException(locations[0].getResponse().getStatusCode(), locations[0].getResponse().getResult().toString());
      }
      return convert2Dtos(locations);
    } catch (Exception e) {
      log.error("call AmadeusFlightAirportLocationSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightOffersSearchAPI failed");
    }

  }

  /**
   * 返回Mock API响应
   */
  private List<LocationDto> mockApiResponse(FlightAirportLocationSearchRequest request) {
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource("mock/locations/locations.json");
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");
      
      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock locations data format is invalid, returning empty list");
        return new ArrayList<>();
      }
      
      List<LocationDto> mockLocations = new ArrayList<>();
      
      // 解析每个位置
      for (JsonNode locationNode : dataNode) {
        LocationDto dto = parseMockLocation(locationNode);
        if (dto != null) {
          mockLocations.add(dto);
        }
      }
      
      log.info("Returned {} mock locations for keyword: {}", 
               mockLocations.size(), request.getKeyword());
      
      return mockLocations;
      
    } catch (IOException e) {
      log.error("Failed to read mock locations file", e);
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to parse mock locations data", e);
      return new ArrayList<>();
    }
  }
  
  /**
   * 解析单个Mock位置
   */
  private LocationDto parseMockLocation(JsonNode locationNode) {
    try {
      LocationDto dto = new LocationDto();
      
      // 设置基本信息
      dto.setType(locationNode.has("type") ? locationNode.get("type").asText() : "location");
      dto.setSubType(locationNode.has("subType") ? locationNode.get("subType").asText() : null);
      dto.setName(locationNode.has("name") ? locationNode.get("name").asText() : null);
      dto.setDetailedName(locationNode.has("detailedName") ? locationNode.get("detailedName").asText() : null);
      dto.setTimeZoneOffset(locationNode.has("timeZoneOffset") ? locationNode.get("timeZoneOffset").asText() : null);
      dto.setIataCode(locationNode.has("iataCode") ? locationNode.get("iataCode").asText() : null);
      
      // 解析地理坐标
      if (locationNode.has("geoCode")) {
        JsonNode geoCodeNode = locationNode.get("geoCode");
        GeoCodeDto geoCodeDto = new GeoCodeDto();
        
        if (geoCodeNode.has("latitude")) {
          geoCodeDto.setLatitude(geoCodeNode.get("latitude").asDouble());
        }
        if (geoCodeNode.has("longitude")) {
          geoCodeDto.setLongitude(geoCodeNode.get("longitude").asDouble());
        }
        
        dto.setGeoCode(geoCodeDto);
      }
      
      // 解析地址信息（如果需要的话，这里暂时跳过，因为Address是Amadeus SDK的类）
      // 可以根据需要添加地址解析逻辑
      
      // 解析相关性分数
      if (locationNode.has("analytics")) {
        JsonNode analyticsNode = locationNode.get("analytics");
        if (analyticsNode.has("travelers")) {
          JsonNode travelersNode = analyticsNode.get("travelers");
          if (travelersNode.has("score")) {
            dto.setRelevance(travelersNode.get("score").asDouble());
          }
        }
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to parse mock location node", e);
      return null;
    }
  }

  /**
   * 判断是否需要使用Mock数据
   */
  private boolean needMock(FlightAirportLocationSearchRequest request) {
    return mockEnabled;
  }

  /**
   * 将Amadeus Location数组转换为LocationDto列表
   */
  private List<LocationDto> convert2Dtos(Location[] locations) {
    List<LocationDto> result = new ArrayList<>();
    
    if (locations == null || locations.length == 0) {
      log.debug("Location array is null or empty");
      return result;
    }
    
    for (Location location : locations) {
      try {
        LocationDto dto = convertSingleLocation(location);
        if (dto != null) {
          result.add(dto);
        }
      } catch (Exception e) {
        log.warn("Failed to convert Location to DTO: {}", e.getMessage(), e);
      }
    }
    
    log.debug("Converted {} Location objects to DTOs", result.size());
    return result;
  }
  
  /**
   * 转换单个Location对象为LocationDto
   */
  private LocationDto convertSingleLocation(Location location) {
    if (location == null) {
      return null;
    }
    
    try {
      LocationDto dto = new LocationDto();
      
      // 设置基本信息
      dto.setType(getStringProperty(location, "type"));
      dto.setSubType(getStringProperty(location, "subType"));
      dto.setName(getStringProperty(location, "name"));
      dto.setDetailedName(getStringProperty(location, "detailedName"));
      dto.setTimeZoneOffset(getStringProperty(location, "timeZoneOffset"));
      dto.setIataCode(getStringProperty(location, "iataCode"));
      
      // 转换地理坐标信息
      if (location.getGeoCode() != null) {
        try {
          GeoCodeDto geoCodeDto = new GeoCodeDto();
          geoCodeDto.setLatitude(location.getGeoCode().getLatitude());
          geoCodeDto.setLongitude(location.getGeoCode().getLongitude());
          dto.setGeoCode(geoCodeDto);
        } catch (Exception e) {
          log.warn("Failed to extract geoCode properties: {}", e.getMessage());
        }
      }
      
      // 设置地址信息（直接使用Amadeus SDK的Address对象）
      if (location.getAddress() != null) {
        dto.setAddress(location.getAddress());
      }
      
      // 转换相关性分数（从analytics中提取）
      if (location.getAnalytics() != null) {
        try {
          // 使用反射安全地获取analytics中的travelers.score
          Double relevance = extractRelevanceScore(location.getAnalytics());
          dto.setRelevance(relevance);
        } catch (Exception e) {
          log.warn("Failed to extract relevance score: {}", e.getMessage());
        }
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to convert single Location to DTO", e);
      return null;
    }
  }
  
  /**
   * 安全地获取Location对象的字符串属性
   */
  private String getStringProperty(Location location, String propertyName) {
    try {
      // 使用反射获取属性值，因为Amadeus SDK的方法名可能与预期不同
      java.lang.reflect.Method getter = findGetter(location.getClass(), propertyName);
      if (getter != null) {
        Object value = getter.invoke(location);
        return value != null ? value.toString() : null;
      }
    } catch (Exception e) {
      log.debug("Failed to get property {} from Location: {}", propertyName, e.getMessage());
    }
    return null;
  }
  
  /**
   * 提取相关性分数
   */
  private Double extractRelevanceScore(Object analytics) {
    try {
      // 尝试获取 analytics.travelers.score
      java.lang.reflect.Method getTravelers = findGetter(analytics.getClass(), "travelers");
      if (getTravelers != null) {
        Object travelers = getTravelers.invoke(analytics);
        if (travelers != null) {
          java.lang.reflect.Method getScore = findGetter(travelers.getClass(), "score");
          if (getScore != null) {
            Object score = getScore.invoke(travelers);
            if (score instanceof Number) {
              return ((Number) score).doubleValue();
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract relevance score: {}", e.getMessage());
    }
    return null;
  }
  
  /**
   * 查找对应属性的getter方法
   */
  private java.lang.reflect.Method findGetter(Class<?> clazz, String propertyName) {
    try {
      // 尝试标准的getter方法名
      String getterName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
      return clazz.getMethod(getterName);
    } catch (NoSuchMethodException e) {
      try {
        // 尝试is开头的方法名（用于boolean类型）
        String isGetterName = "is" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return clazz.getMethod(isGetterName);
      } catch (NoSuchMethodException e2) {
        // 尝试直接使用属性名
        try {
          return clazz.getMethod(propertyName);
        } catch (NoSuchMethodException e3) {
          log.debug("No getter found for property: {} in class: {}", propertyName, clazz.getSimpleName());
          return null;
        }
      }
    }
  }

}
