package com.pkfare.trip.scale.api.amadeus.activities;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Activity;
import com.amadeus.resources.Activity.GeoCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import com.pkfare.trip.scale.api.amadeus.activities.response.ElementaryPriceDto;
import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AmadeusActivitiesSearchApi {

  @Value("${amadeus.activities.mock.enabled:true}")
  private boolean mockEnabled;
  
  private final ObjectMapper objectMapper = new ObjectMapper();


  public List<ActivityDto> searchActivities(ActivitiesSearchRequest activitiesSearchRequest) {
    log.info("activitiesSearchRequest:{}",activitiesSearchRequest);
    if(needMock(activitiesSearchRequest)){
      return mockApiResponse(activitiesSearchRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("latitude", activitiesSearchRequest.getLatitude())
        .and("longitude", activitiesSearchRequest.getLongitude())
        .and("radius", activitiesSearchRequest.getRadius());

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
   */
  private boolean needMock(ActivitiesSearchRequest activitiesSearchRequest) {
    return mockEnabled;
  }

  /**
   * 返回Mock API响应
   */
  private List<ActivityDto> mockApiResponse(ActivitiesSearchRequest request) {
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource("mock/activities-mock.json");
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");
      
      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock activities data format is invalid, returning empty list");
        return new ArrayList<>();
      }
      
      List<ActivityDto> mockActivities = new ArrayList<>();
      
      // 解析每个活动
      for (JsonNode activityNode : dataNode) {
        ActivityDto dto = parseMockActivity(activityNode);
        if (dto != null) {
          mockActivities.add(dto);
        }
      }
      
      log.info("Returned {} mock activities for latitude: {}, longitude: {}, radius: {}", 
               mockActivities.size(), request.getLatitude(), request.getLongitude(), request.getRadius());
      
      return mockActivities;
      
    } catch (IOException e) {
      log.error("Failed to read mock activities data", e);
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

  // 辅助方法：安全获取字符串值
  private String getStringValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
  }


}
