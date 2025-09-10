package com.pkfare.trip.scale.api.amadeus.activities;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Activity;
import com.amadeus.resources.Activity.GeoCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import com.pkfare.trip.scale.api.amadeus.activities.response.ElementaryPriceDto;
import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.cache.AbstractMockDataProcessor;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Component
public class AmadeusActivitiesSearchApi extends AbstractMockDataProcessor {

  @Value("${amadeus.activities.mock.enabled:true}")
  private boolean mockEnabled;


  public List<ActivityDto> searchActivities(ActivitiesSearchRequest activitiesSearchRequest) {
    log.info("activitiesSearchRequest:{}",activitiesSearchRequest);
    if(needMock(activitiesSearchRequest)){
      return mockApiResponse(activitiesSearchRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("latitude", activitiesSearchRequest.getLatitude())
        .and("longitude", activitiesSearchRequest.getLongitude())
        .and("radius", activitiesSearchRequest.getRadius());
    if (StringUtils.isNotBlank(activitiesSearchRequest.getCurrency())) {
      params.and("currency", activitiesSearchRequest.getCurrency());
    }
    if (StringUtils.isNotBlank(activitiesSearchRequest.getCategoryGroup())) {
      params.and("categoryGroup", activitiesSearchRequest.getCategoryGroup());
    }

    try {
      Activity[] activities = amadeus.shopping.activities.get(params);
      if (activities == null || activities.length == 0) {
        log.error("call AmadeusActivitiesSearchApi return empty，resonse:{} ", activities);
        return Lists.newArrayList();
      }
      if (activities[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusActivitiesSearchApi failed，resonse：{}", activities[0].getResponse());
        throw new AmadeusApiException(activities[0].getResponse().getStatusCode(), activities[0].getResponse().getResult().toString());
      }
      return convert2Dtos(activities);
    } catch (Exception e) {
      log.error("call AmadeusActivitiesSearchApi failed", e);
      throw new AmadeusApiException(500, "call AmadeusActivitiesSearchApi failed");
    }

  }

  /**
   * 判断是否需要使用Mock数据
   * 先判断开关是否打开，然后检查缓存或文件是否存在
   */
  private boolean needMock(ActivitiesSearchRequest activitiesSearchRequest) {
    if (!mockEnabled) {
      return false;
    }
    
    // 优先检查缓存中是否有数据
    if (mockDataCacheManager.isCacheInitialized()) {
      JsonNode cachedData = mockDataCacheManager.getActivitiesMockDataArray(
          activitiesSearchRequest.getLatitude(), activitiesSearchRequest.getLongitude());
      if (cachedData != null) {
        log.debug("Found cached mock data for latitude: {}, longitude: {}", 
                  activitiesSearchRequest.getLatitude(), activitiesSearchRequest.getLongitude());
        return true;
      }
    }
    
    // 缓存未命中，检查是否存在对应经纬度的mock文件
    String mockFilePath = buildMockFilePath(activitiesSearchRequest.getLatitude(), activitiesSearchRequest.getLongitude());
    try {
      org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(mockFilePath);
      boolean exists = resource.exists();
      log.debug("Checking mock file for latitude: {}, longitude: {} - path: {} - exists: {}", 
                activitiesSearchRequest.getLatitude(), activitiesSearchRequest.getLongitude(), mockFilePath, exists);
      return exists;
    } catch (Exception e) {
      log.debug("Failed to check mock file for latitude: {}, longitude: {}: {}", 
                activitiesSearchRequest.getLatitude(), activitiesSearchRequest.getLongitude(), e.getMessage());
      return false;
    }
  }
  
  /**
   * 构建mock文件路径
   */
  private String buildMockFilePath(Double latitude, Double longitude) {
    return String.format("mock/activities/%s-%s.json", latitude, longitude);
  }

  /**
   * 返回Mock API响应
   * 优先从缓存获取，缓存未命中时回退到文件读取
   */
  private List<ActivityDto> mockApiResponse(ActivitiesSearchRequest request) {
    long startTime = System.currentTimeMillis();
    
    // 构建缓存键和文件路径
    String cacheKey = String.format("mock/activities/%s-%s.json", request.getLatitude(), request.getLongitude());
    String fallbackFilePath = buildMockFilePath(request.getLatitude(), request.getLongitude());
    
    try {
      // 使用AbstractMockDataProcessor的缓存优先逻辑获取数据
      JsonNode dataArray = getMockDataArray(cacheKey, fallbackFilePath);
      
      if (dataArray == null) {
        log.warn("No mock activities data found for latitude: {}, longitude: {}", 
                 request.getLatitude(), request.getLongitude());
        return new ArrayList<>();
      }
      
      // 使用AbstractMockDataProcessor的通用解析方法
      List<ActivityDto> mockActivities = parseMockDataArray(dataArray, this::parseMockActivity);
      
      long duration = System.currentTimeMillis() - startTime;
      log.info("Returned {} mock activities for latitude: {}, longitude: {}, radius: {} in {} ms", 
               mockActivities.size(), request.getLatitude(), request.getLongitude(), request.getRadius(), duration);
      
      return mockActivities;
      
    } catch (Exception e) {
      log.error("Failed to get mock activities data for latitude: {}, longitude: {}", 
                request.getLatitude(), request.getLongitude(), e);
      return new ArrayList<>();
    }
  }

  /**
   * 解析单个Mock活动
   */
  private ActivityDto parseMockActivity(JsonNode activityNode) {
    try {
      ActivityDto dto = new ActivityDto();
      
      // 设置基本属性
      dto.setType(getStringValue(activityNode, "type"));
      dto.setId(getStringValue(activityNode, "id"));
      dto.setName(getStringValue(activityNode, "name"));
      dto.setShortDescription(getStringValue(activityNode, "shortDescription"));
      dto.setDescription(getStringValue(activityNode, "description"));
      dto.setRating(getStringValue(activityNode, "rating"));
      dto.setBookingLink(getStringValue(activityNode, "bookingLink"));
      dto.setMinimumDuration(getStringValue(activityNode, "minimumDuration"));
      
      // 解析地理位置信息
      JsonNode geoCodeNode = activityNode.get("geoCode");
      if (geoCodeNode != null) {
        // 创建GeoCode对象 - 注意这里使用Amadeus SDK的GeoCode类
        GeoCodeDto geoCode = new GeoCodeDto();
        if (geoCodeNode.has("latitude")) {
          geoCode.setLatitude(geoCodeNode.get("latitude").asDouble());
        }
        if (geoCodeNode.has("longitude")) {
          geoCode.setLongitude(geoCodeNode.get("longitude").asDouble());
        }
        dto.setGeoCode(geoCode);
      }
      
      // 解析价格信息
      JsonNode priceNode = activityNode.get("price");
      if (priceNode != null) {
        ElementaryPriceDto priceDto = new ElementaryPriceDto();
        priceDto.setAmount(getStringValue(priceNode, "amount"));
        priceDto.setCurrencyCode(getStringValue(priceNode, "currencyCode"));
        dto.setPrice(priceDto);
      }
      
      // 解析图片列表
      JsonNode picturesNode = activityNode.get("pictures");
      if (picturesNode != null && picturesNode.isArray()) {
        List<String> pictures = new ArrayList<>();
        for (JsonNode pictureNode : picturesNode) {
          if (pictureNode.isTextual()) {
            pictures.add(pictureNode.asText());
          }
        }
        dto.setPictures(pictures);
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to parse mock activity", e);
      return null;
    }
  }

  /**
   * 转换Amadeus Activity数组为DTO列表
   */
  private List<ActivityDto> convert2Dtos(Activity[] activities) {
    if (activities == null || activities.length == 0) {
      return new ArrayList<>();
    }

    List<ActivityDto> activityDtos = new ArrayList<>();
    
    for (Activity activity : activities) {
      if (activity == null) {
        continue;
      }
      
      ActivityDto dto = new ActivityDto();
      
      // 设置基本属性
      dto.setType(activity.getType());
      dto.setId(activity.getId());
      dto.setName(activity.getName());
      dto.setShortDescription(activity.getShortDescription());
      dto.setDescription(activity.getDescription());
      dto.setRating(activity.getRating());
      dto.setBookingLink(activity.getBookingLink());
      dto.setMinimumDuration(activity.getMinimumDuration());
      
      // 转换地理位置信息
      if (activity.getGeoCode() != null) {
        dto.setGeoCode(converGeoCode(activity.getGeoCode()));
      }
      
      // 转换价格信息
      if (activity.getPrice() != null) {
        ElementaryPriceDto priceDto = new ElementaryPriceDto();
        priceDto.setAmount(activity.getPrice().getAmount());
        priceDto.setCurrencyCode(activity.getPrice().getCurrencyCode());
        dto.setPrice(priceDto);
      }
      
      // 转换图片列表
      if (activity.getPictures() != null && activity.getPictures().length > 0) {
        List<String> pictures = new ArrayList<>();
        for (String picture : activity.getPictures()) {
          pictures.add(picture);
        }
        dto.setPictures(pictures);
      }
      
      activityDtos.add(dto);
    }
    
    return activityDtos;
  }

  private GeoCodeDto converGeoCode(GeoCode geoCode) {
    if(Objects.isNull(geoCode)){
      return null;
    }
    GeoCodeDto dto = new GeoCodeDto();
    dto.setLatitude(geoCode.getLatitude());
    dto.setLongitude(geoCode.getLongitude());
    return dto;
  }

}
