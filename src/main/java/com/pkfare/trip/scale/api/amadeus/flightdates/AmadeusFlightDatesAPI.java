package com.pkfare.trip.scale.api.amadeus.flightdates;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightDate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightdates.response.FlightDateDto;
import com.pkfare.trip.scale.api.amadeus.flightdates.response.FlightDatePriceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Component
public class AmadeusFlightDatesAPI {

  @Value("${amadeus.flight.dates.mock.enabled:true}")
  private boolean mockEnabled;
  
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  public List<FlightDateDto> flightDates(FlightDatesRequest flightDatesRequest) {
    log.info("flightDatesRequest:{}",flightDatesRequest);
    if(needMock(flightDatesRequest)){
      return mockApiResponse(flightDatesRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("origin", flightDatesRequest.getOrigin()).and("destination", flightDatesRequest.getDestination())
        .and("departureDate", flightDatesRequest.getDepartureDate())
        .and("oneWay", flightDatesRequest.getOneWay()).and("nonStop", flightDatesRequest.getNonStop())
        .and("maxPrice", flightDatesRequest.getMaxPrice());
    if(StringUtils.isNotBlank(flightDatesRequest.getDuration())){
      params.and("duration", flightDatesRequest.getDuration());
    }
    try {
      FlightDate[] flightDates = amadeus.shopping.flightDates.get(params);
      if (flightDates == null || flightDates.length == 0) {
        log.error("call AmadeusFlightDatesAPI return empty，resonse:{} ", flightDates);
        return Lists.newArrayList();
      }
      if (flightDates[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightDatesAPI failed，resonse：{}", flightDates[0].getResponse());
        throw new AmadeusApiException(flightDates[0].getResponse().getStatusCode(), flightDates[0].getResponse().getResult().toString());
      }
      return convert2Dtos(flightDates);
    } catch (Exception e) {
      log.error("call AmadeusFlightDatesAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightDatesAPI failed");
    }

  }

  /**
   * 返回Mock API响应
   */
  private List<FlightDateDto> mockApiResponse(FlightDatesRequest flightDatesRequest) {
    String origin = flightDatesRequest.getOrigin();
    String destination = flightDatesRequest.getDestination();
    String mockFileName = String.format("mock/flightdates/%s-%s.json", origin, destination);
    
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource(mockFileName);
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");
      
      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock flight dates data format is invalid in file {}, returning empty list", mockFileName);
        return new ArrayList<>();
      }
      
      List<FlightDateDto> mockFlightDates = new ArrayList<>();
      
      // 解析每个航班日期
      for (JsonNode flightDateNode : dataNode) {
        FlightDateDto dto = parseMockFlightDate(flightDateNode, flightDatesRequest);
        if (dto != null) {
          mockFlightDates.add(dto);
        }
      }
      
      log.info("Returned {} mock flight dates for route: {} -> {} from file: {}", 
               mockFlightDates.size(), origin, destination, mockFileName);
      
      return mockFlightDates;
      
    } catch (IOException e) {
      log.error("Failed to read mock flight dates data from file: {}", mockFileName, e);
      return new ArrayList<>();
    }
  }
  
  /**
   * 解析单个Mock航班日期并替换请求参数
   */
  private FlightDateDto parseMockFlightDate(JsonNode flightDateNode, FlightDatesRequest request) {
    try {
      FlightDateDto dto = new FlightDateDto();
      
      // 设置基本信息
      dto.setType(flightDateNode.has("type") ? flightDateNode.get("type").asText() : "flight-date");
      dto.setOrigin(request.getOrigin());
      dto.setDestination(request.getDestination());
      
      // 解析出发日期
      if (flightDateNode.has("departureDate")) {
        String departureDateStr = flightDateNode.get("departureDate").asText();
        try {
          Date departureDate = dateFormat.parse(departureDateStr);
          dto.setDepartureDate(departureDate);
        } catch (ParseException e) {
          log.warn("Failed to parse departure date: {}", departureDateStr, e);
        }
      }
      
      // 解析返回日期（如果存在）
      if (flightDateNode.has("returnDate")) {
        String returnDateStr = flightDateNode.get("returnDate").asText();
        try {
          Date returnDate = dateFormat.parse(returnDateStr);
          dto.setReturnDate(returnDate);
        } catch (ParseException e) {
          log.warn("Failed to parse return date: {}", returnDateStr, e);
        }
      }
      
      // 解析价格信息
      if (flightDateNode.has("price")) {
        JsonNode priceNode = flightDateNode.get("price");
        FlightDatePriceDto priceDto = new FlightDatePriceDto();
        
        if (priceNode.has("total")) {
          String totalStr = priceNode.get("total").asText();
          try {
            double total = Double.parseDouble(totalStr);
            priceDto.setTotal(total);
          } catch (NumberFormatException e) {
            log.warn("Failed to parse price total: {}", totalStr, e);
            priceDto.setTotal(0.0);
          }
        }
        
        dto.setPrice(priceDto);
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to parse mock flight date node", e);
      return null;
    }
  }

  /**
   * 判断是否需要使用Mock数据
   * 1. 检查mock开关是否打开
   * 2. 检查是否存在对应的origin-destination.json文件
   */
  private boolean needMock(FlightDatesRequest flightDatesRequest) {
    if (!mockEnabled) {
      log.debug("Mock is disabled for flight dates");
      return false;
    }
    
    String origin = flightDatesRequest.getOrigin();
    String destination = flightDatesRequest.getDestination();
    
    if (StringUtils.isBlank(origin) || StringUtils.isBlank(destination)) {
      log.debug("Origin or destination is blank, cannot use mock data");
      return false;
    }
    
    String mockFileName = String.format("mock/flightdates/%s-%s.json", origin, destination);
    ClassPathResource resource = new ClassPathResource(mockFileName);
    
    boolean exists = resource.exists();
    log.debug("Mock file {} exists: {}", mockFileName, exists);
    
    return exists;
  }

  /**
   * 将 Amadeus FlightDate 数组转换为 FlightDateDto 列表
   */
  private List<FlightDateDto> convert2Dtos(FlightDate[] flightDates) {
    List<FlightDateDto> result = new ArrayList<>();
    
    if (flightDates == null || flightDates.length == 0) {
      log.debug("FlightDate array is null or empty");
      return result;
    }
    
    for (FlightDate flightDate : flightDates) {
      try {
        FlightDateDto dto = convertSingleFlightDate(flightDate);
        if (dto != null) {
          result.add(dto);
        }
      } catch (Exception e) {
        log.warn("Failed to convert FlightDate to DTO: {}", e.getMessage(), e);
      }
    }
    
    log.debug("Converted {} FlightDate objects to DTOs", result.size());
    return result;
  }
  
  /**
   * 转换单个 FlightDate 对象为 FlightDateDto
   */
  private FlightDateDto convertSingleFlightDate(FlightDate flightDate) {
    if (flightDate == null) {
      return null;
    }
    
    try {
      FlightDateDto dto = new FlightDateDto();
      
      // 设置基本信息
      dto.setType(flightDate.getType() != null ? flightDate.getType() : "flight-date");
      dto.setOrigin(flightDate.getOrigin());
      dto.setDestination(flightDate.getDestination());
      dto.setDepartureDate(flightDate.getDepartureDate());
      dto.setReturnDate(flightDate.getReturnDate());
      
      // 转换价格信息
      if (flightDate.getPrice() != null) {
        FlightDatePriceDto priceDto = new FlightDatePriceDto();
        priceDto.setTotal(flightDate.getPrice().getTotal());
        dto.setPrice(priceDto);
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to convert single FlightDate to DTO", e);
      return null;
    }
  }

}
