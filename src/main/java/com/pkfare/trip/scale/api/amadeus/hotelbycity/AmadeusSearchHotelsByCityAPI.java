package com.pkfare.trip.scale.api.amadeus.hotelbycity;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Hotel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelAddressDto;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByGeocodeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class AmadeusSearchHotelsByCityAPI {

  @Value("${amadeus.hotelofcity.mock.enabled:true}")
  private boolean mockEnabled;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<HotelInfoDto> queryHotelByCity(QueryHotelByCityRequest queryHotelByCityRequest) throws AmadeusApiException {
    if(needMock(queryHotelByCityRequest.getCityCode())){
      return mockApiResponseByCityCode(queryHotelByCityRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("cityCode", queryHotelByCityRequest.getCityCode())
        .and("radius", queryHotelByCityRequest.getRadius())
        .and("radiusUnit", queryHotelByCityRequest.getRadiusUnit()).and("ratings", queryHotelByCityRequest.getRatings());

    try {
      Hotel[] hotels = amadeus.referenceData.locations.hotels.byCity.get(params);
      if (hotels == null || hotels.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotels);
        return Lists.newArrayList();
      }
      if (hotels[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotels[0].getResponse());
        throw new AmadeusApiException(hotels[0].getResponse().getStatusCode(), hotels[0].getResponse().getResult().toString());
      }
      return convert2Dtos(hotels);
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }

  }

  public List<HotelInfoDto> queryHotelByGeocode(QueryHotelByGeocodeRequest request) throws AmadeusApiException {
    if (mockEnabled) {
      return mockHotelByGeocode(request);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("latitude", request.getLatitude())
        .and("longitude", request.getLongitude())
        .and("radius", request.getRadius())
        .and("radiusUnit", request.getRadiusUnit())
        .and("ratings", request.getRatings());
    if (!CollectionUtils.isEmpty(request.getRatings())) {
      params.and("ratings", request.getRatings());
    }
    if (!CollectionUtils.isEmpty(request.getAmenities())) {
      params.and("amenities", request.getAmenities());
    }

    try {
      Hotel[] hotels = amadeus.referenceData.locations.hotels.byGeocode.get(params);
      if (hotels == null || hotels.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotels);
        return convert2Dtos(hotels);
      }
      if (hotels[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotels[0].getResponse());
        throw new AmadeusApiException(hotels[0].getResponse().getStatusCode(), hotels[0].getResponse().getResult().toString());
      }
      return convert2Dtos(hotels);
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }
  }

  /**
   * 返回Mock API响应
   */
  private List<HotelInfoDto> mockHotelByGeocode(QueryHotelByGeocodeRequest request) {
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource("mock/hotelbygeocode/hotel-by-geocode.json");
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");

      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock hotel of city data format is invalid, returning empty list");
        return new ArrayList<>();
      }

      // 解析每个酒店
      List<HotelInfoDto> resultList = new ArrayList<>();
      for (JsonNode hotelNode : dataNode) {
        HotelInfoDto dto = parseMockHotel(hotelNode);
        if (dto != null) {
          resultList.add(dto);
        }
      }

      log.info("Returned {} mock hotels for geocode", resultList.size());
      return resultList;
    } catch (IOException e) {
      log.error("Failed to read mock hotel of city file", e);
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to parse mock hotel of city data", e);
      return new ArrayList<>();
    }
  }

  /**
   * 根据cityCode返回Mock API响应
   */
  private List<HotelInfoDto> mockApiResponseByCityCode(QueryHotelByCityRequest queryHotelByCityRequest) {
    String cityCode = queryHotelByCityRequest.getCityCode();
    String mockFilePath = "mock/hotelofcity/" + cityCode + ".json";
    
    try {
      // 读取对应cityCode的mock JSON文件
      ClassPathResource resource = new ClassPathResource(mockFilePath);
      if (!resource.exists()) {
        log.warn("Mock file not found for cityCode: {} at path: {}", cityCode, mockFilePath);
        return new ArrayList<>();
      }
      
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");

      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock hotel data format is invalid for cityCode: {}, returning empty list", cityCode);
        return new ArrayList<>();
      }

      List<HotelInfoDto> mockHotels = new ArrayList<>();

      // 解析每个酒店
      for (JsonNode hotelNode : dataNode) {
        HotelInfoDto dto = parseMockHotel(hotelNode);
        if (dto != null) {
          mockHotels.add(dto);
        }
      }

      log.info("Returned {} mock hotels for city code: {} from file: {}", 
               mockHotels.size(), cityCode, mockFilePath);

      return mockHotels;

    } catch (IOException e) {
      log.error("Failed to read mock hotel file for cityCode: {} at path: {}", cityCode, mockFilePath, e);
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to parse mock hotel data for cityCode: {}", cityCode, e);
      return new ArrayList<>();
    }
  }

  /**
   * 返回Mock API响应（保留原方法作为fallback）
   */
  private List<HotelInfoDto> mockApiResponse(QueryHotelByCityRequest queryHotelByCityRequest) {
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource("mock/hotelofcity/hotel-of-city.json");
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");

      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock hotel of city data format is invalid, returning empty list");
        return new ArrayList<>();
      }

      List<HotelInfoDto> mockHotels = new ArrayList<>();

      // 解析每个酒店
      for (JsonNode hotelNode : dataNode) {
        HotelInfoDto dto = parseMockHotel(hotelNode);
        if (dto != null) {
          mockHotels.add(dto);
        }
      }

      log.info("Returned {} mock hotels for city code: {}",
               mockHotels.size(), queryHotelByCityRequest.getCityCode());

      return mockHotels;

    } catch (IOException e) {
      log.error("Failed to read mock hotel of city file", e);
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to parse mock hotel of city data", e);
      return new ArrayList<>();
    }
  }

  /**
   * 解析单个Mock酒店
   */
  private HotelInfoDto parseMockHotel(JsonNode hotelNode) {
    try {
      HotelInfoDto dto = new HotelInfoDto();

      // 设置基本信息
      dto.setSubtype(hotelNode.has("subType") ? hotelNode.get("subType").asText() : null);
      dto.setName(hotelNode.has("name") ? hotelNode.get("name").asText() : null);
      dto.setTimeZoneName(hotelNode.has("timeZoneName") ? hotelNode.get("timeZoneName").asText() : null);
      dto.setIataCode(hotelNode.has("iataCode") ? hotelNode.get("iataCode").asText() : null);
      dto.setHotelId(hotelNode.has("hotelId") ? hotelNode.get("hotelId").asText() : null);
      dto.setChainCode(hotelNode.has("chainCode") ? hotelNode.get("chainCode").asText() : null);
      dto.setLastUpdate(hotelNode.has("lastUpdate") ? hotelNode.get("lastUpdate").asText() : null);

      // 解析地理坐标
      if (hotelNode.has("geoCode")) {
        JsonNode geoCodeNode = hotelNode.get("geoCode");
        GeoCodeDto geoCodeDto = new GeoCodeDto();

        if (geoCodeNode.has("latitude")) {
          geoCodeDto.setLatitude(geoCodeNode.get("latitude").asDouble());
        }
        if (geoCodeNode.has("longitude")) {
          geoCodeDto.setLongitude(geoCodeNode.get("longitude").asDouble());
        }

        dto.setGeoCode(geoCodeDto);
      }

      // 解析地址信息
      if (hotelNode.has("address")) {
        JsonNode addressNode = hotelNode.get("address");
        HotelAddressDto addressDto = new HotelAddressDto();

        if (addressNode.has("cityName")) {
          addressDto.setCityName(addressNode.get("cityName").asText());
        }
        if (addressNode.has("countryCode")) {
          addressDto.setCountryCode(addressNode.get("countryCode").asText());
        }
        if (addressNode.has("postalCode")) {
          addressDto.setPostalCode(addressNode.get("postalCode").asText());
        }
        if (addressNode.has("stateCode")) {
          addressDto.setStateCode(addressNode.get("stateCode").asText());
        }

        dto.setAddress(addressDto);
      }

      return dto;

    } catch (Exception e) {
      log.error("Failed to parse mock hotel node", e);
      return null;
    }
  }

  /**
   * 判断是否需要使用Mock数据
   * 先判断开关是否打开，然后判断mock/hotelofcity/下是否有对应cityCode的文件
   */
  private boolean needMock(String cityCode) {
    if (!mockEnabled) {
      return false;
    }
    
    // 检查是否存在对应cityCode的mock文件
    String mockFilePath = "mock/hotelofcity/" + cityCode + ".json";
    try {
      ClassPathResource resource = new ClassPathResource(mockFilePath);
      boolean exists = resource.exists();
      log.debug("Checking mock file for cityCode {}: {} - exists: {}", cityCode, mockFilePath, exists);
      return exists;
    } catch (Exception e) {
      log.debug("Failed to check mock file for cityCode {}: {}", cityCode, e.getMessage());
      return false;
    }
  }

  /**
   * 将 Amadeus Hotel 数组转换为 HotelInfoDto 列表
   */
  private List<HotelInfoDto> convert2Dtos(Hotel[] hotels) {
    List<HotelInfoDto> result = new ArrayList<>();

    if (hotels == null || hotels.length == 0) {
      log.debug("Hotel array is null or empty");
      return result;
    }

    for (Hotel hotel : hotels) {
      try {
        HotelInfoDto dto = convertSingleHotel(hotel);
        if (dto != null) {
          result.add(dto);
        }
      } catch (Exception e) {
        log.warn("Failed to convert Hotel to DTO: {}", e.getMessage(), e);
      }
    }

    log.debug("Converted {} Hotel objects to DTOs", result.size());
    return result;
  }

  /**
   * 转换单个 Hotel 对象为 HotelInfoDto
   */
  private HotelInfoDto convertSingleHotel(Hotel hotel) {
    if (hotel == null) {
      return null;
    }

    try {
      HotelInfoDto dto = new HotelInfoDto();

      // 设置基本信息
      // 注意：由于Amadeus SDK的实际方法名可能与预期不同，这里使用安全的方式获取属性
      try {
        dto.setSubtype(getStringProperty(hotel, "subType"));
        dto.setName(getStringProperty(hotel, "name"));
        dto.setTimeZoneName(getStringProperty(hotel, "timeZoneName"));
        dto.setIataCode(getStringProperty(hotel, "iataCode"));
        dto.setHotelId(getStringProperty(hotel, "hotelId"));
        dto.setChainCode(getStringProperty(hotel, "chainCode"));
        dto.setGooglePlaceId(getStringProperty(hotel, "googlePlaceId"));
        dto.setOpenjetAirportId(getStringProperty(hotel, "openjetAirportId"));
        dto.setUicCode(getStringProperty(hotel, "uicCode"));
        dto.setLastUpdate(getStringProperty(hotel, "lastUpdate"));
      } catch (Exception e) {
        log.warn("Failed to extract basic hotel properties: {}", e.getMessage());
      }

      // 转换地理坐标信息
      if (hotel.getGeoCode() != null) {
        GeoCodeDto geoCodeDto = new GeoCodeDto();
        geoCodeDto.setLatitude(hotel.getGeoCode().getLatitude());
        geoCodeDto.setLongitude(hotel.getGeoCode().getLongitude());
        dto.setGeoCode(geoCodeDto);
      }

      // 转换地址信息
      if (hotel.getAddress() != null) {
        try {
          HotelAddressDto addressDto = new HotelAddressDto();
          addressDto.setCityName(getAddressProperty(hotel.getAddress(), "cityName"));
          addressDto.setCountryCode(getAddressProperty(hotel.getAddress(), "countryCode"));
          addressDto.setPostalCode(getAddressProperty(hotel.getAddress(), "postalCode"));
          addressDto.setStateCode(getAddressProperty(hotel.getAddress(), "stateCode"));
          dto.setAddress(addressDto);
        } catch (Exception e) {
          log.warn("Failed to extract address properties: {}", e.getMessage());
        }
      }

      return dto;

    } catch (Exception e) {
      log.error("Failed to convert single Hotel to DTO", e);
      return null;
    }
  }

  /**
   * 安全地获取Hotel对象的字符串属性
   */
  private String getStringProperty(Hotel hotel, String propertyName) {
    try {
      // 使用反射获取属性值，因为Amadeus SDK的方法名可能与预期不同
      java.lang.reflect.Method getter = findGetter(hotel.getClass(), propertyName);
      if (getter != null) {
        Object value = getter.invoke(hotel);
        return value != null ? value.toString() : null;
      }
    } catch (Exception e) {
      log.debug("Failed to get property {} from Hotel: {}", propertyName, e.getMessage());
    }
    return null;
  }

  /**
   * 安全地获取Address对象的字符串属性
   */
  private String getAddressProperty(Object address, String propertyName) {
    try {
      java.lang.reflect.Method getter = findGetter(address.getClass(), propertyName);
      if (getter != null) {
        Object value = getter.invoke(address);
        return value != null ? value.toString() : null;
      }
    } catch (Exception e) {
      log.debug("Failed to get property {} from Address: {}", propertyName, e.getMessage());
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
