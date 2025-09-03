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

  private List<LocationDto> convert2Dtos(Location[] locations) {
    return Lists.newArrayList();
  }

}
