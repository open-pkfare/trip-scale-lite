package com.pkfare.trip.scale.api.amadeus.flightoffers;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightOfferSearch;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.response.FlightOfferDto;
import com.pkfare.trip.scale.api.amadeus.flightoffers.response.ItineraryDto;
import com.pkfare.trip.scale.api.amadeus.flightoffers.response.SearchPriceDto;
import com.pkfare.trip.scale.api.amadeus.flightoffers.response.SearchSegmentDto;
import com.pkfare.trip.scale.api.amadeus.flightoffers.response.AirportInfoDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AmadeusFlightOffersSearchAPI {

  @Value("${amadeus.flight.offers.mock.enabled:true}")
  private boolean mockEnabled;
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<FlightOfferDto> flightOffersSearch(FlightOffersSearchRequest flightOffersSearchRequest) {
    if(needMock(flightOffersSearchRequest)){
      return mockApiResponse(flightOffersSearchRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("originLocationCode", flightOffersSearchRequest.getOrigin())
        .and("destinationLocationCode", flightOffersSearchRequest.getDestination())
        .and("departureDate", flightOffersSearchRequest.getDepartureDate())
        .and("adults", flightOffersSearchRequest.getAdults()).and("children", flightOffersSearchRequest.getChildren())
        .and("infants", flightOffersSearchRequest.getInfants()).and("nonStop", flightOffersSearchRequest.getNonStop())
        .and("currencyCode", flightOffersSearchRequest.getCurrency()).and("maxPrice", flightOffersSearchRequest.getMaxPrice())
        .and("max", flightOffersSearchRequest.getMax());
    if(!Objects.isNull(flightOffersSearchRequest.getReturnDate())){
      params.and("returnDate", flightOffersSearchRequest.getReturnDate());
    }
    try {
      FlightOfferSearch[] flightOffers = amadeus.shopping.flightOffersSearch.get(params);
      if (flightOffers == null || flightOffers.length == 0) {
        log.error("call AmadeusFlightOffersSearchAPI return empty，resonse:{} ", flightOffers);
        return Lists.newArrayList();
      }
      if (flightOffers[0].getResponse().getStatusCode() != 200) {
       log.error("call AmadeusFlightOffersSearchAPI failed，resonse：{}", flightOffers[0].getResponse());
       throw new AmadeusApiException(flightOffers[0].getResponse().getStatusCode(), flightOffers[0].getResponse().getResult().toString());
       }
      return convertFlights(flightOffers);
    } catch (Exception e) {
      log.error("call AmadeusFlightOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightOffersSearchAPI failed");
    }

  }

  private List<FlightOfferDto> convertFlights(FlightOfferSearch[] flightOffers) {
    if (flightOffers == null || flightOffers.length == 0) {
      return new ArrayList<>();
    }

    List<FlightOfferDto> flightOfferDtos = new ArrayList<>();
    
    for (FlightOfferSearch flightOffer : flightOffers) {
      if (flightOffer == null) {
        continue;
      }
      
      FlightOfferDto dto = new FlightOfferDto();
      
      // 设置基本属性
      dto.setType(flightOffer.getType());
      dto.setId(flightOffer.getId());
      dto.setSource(flightOffer.getSource());
      dto.setInstantTicketingRequired(flightOffer.isInstantTicketingRequired());
      dto.setDisablePricing(flightOffer.isDisablePricing() );
      dto.setNonHomogeneous(flightOffer.isNonHomogeneous());
      dto.setOneWay(flightOffer.isOneWay() );
      dto.setPaymentCardRequired(flightOffer.isPaymentCardRequired());
      dto.setLastTicketingDate(flightOffer.getLastTicketingDate());
      dto.setNumberOfBookableSeats(flightOffer.getNumberOfBookableSeats());
      
      // 转换价格信息
      if (flightOffer.getPrice() != null) {
        SearchPriceDto priceDto = new SearchPriceDto();
        priceDto.setCurrency(flightOffer.getPrice().getCurrency());
        priceDto.setTotal(flightOffer.getPrice().getTotal());
        dto.setPrice(priceDto);
      }
      
      // 转换行程信息
      if (flightOffer.getItineraries() != null && flightOffer.getItineraries().length > 0) {
        List<ItineraryDto> itineraryDtos = new ArrayList<>();
        
        for (com.amadeus.resources.FlightOfferSearch.Itinerary itinerary : flightOffer.getItineraries()) {
          if (itinerary == null) {
            continue;
          }
          
          ItineraryDto itineraryDto = new ItineraryDto();
          itineraryDto.setDuration(itinerary.getDuration());
          
          // 转换航段信息
          if (itinerary.getSegments() != null && itinerary.getSegments().length > 0) {
            List<SearchSegmentDto> segmentDtos = new ArrayList<>();
            
            for (com.amadeus.resources.FlightOfferSearch.SearchSegment segment : itinerary.getSegments()) {
              if (segment == null) {
                continue;
              }
              
              SearchSegmentDto segmentDto = new SearchSegmentDto();
              segmentDto.setId(segment.getId());
              segmentDto.setCarrierCode(segment.getCarrierCode());
              segmentDto.setNumber(segment.getNumber());
              segmentDto.setDuration(segment.getDuration());
              segmentDto.setNumberOfStops(segment.getNumberOfStops());
              segmentDto.setBlacklistedInEU(segment.isBlacklistedInEU());
              
              // 转换出发信息
              if (segment.getDeparture() != null) {
                AirportInfoDto departure = new AirportInfoDto();
                departure.setIataCode(segment.getDeparture().getIataCode());
                departure.setTerminal(segment.getDeparture().getTerminal());
                departure.setAt(segment.getDeparture().getAt());
                segmentDto.setDeparture(departure);
              }
              
              // 转换到达信息
              if (segment.getArrival() != null) {
                AirportInfoDto arrival = new AirportInfoDto();
                arrival.setIataCode(segment.getArrival().getIataCode());
                arrival.setTerminal(segment.getArrival().getTerminal());
                arrival.setAt(segment.getArrival().getAt());
                segmentDto.setArrival(arrival);
              }
              
              segmentDtos.add(segmentDto);
            }
            
            itineraryDto.setSegments(segmentDtos);
          }
          
          itineraryDtos.add(itineraryDto);
        }
        
        dto.setItineraries(itineraryDtos);
      }
      
      flightOfferDtos.add(dto);
    }
    
    return flightOfferDtos;
  }

  /**
   * 判断是否需要使用Mock数据
   */
  private boolean needMock(FlightOffersSearchRequest request) {
    log.debug("AmadeusFlightOffersSearchAPI mockEnabled value: {}", mockEnabled);
    return mockEnabled;
  }

  /**
   * 返回Mock API响应
   */
  private List<FlightOfferDto> mockApiResponse(FlightOffersSearchRequest request) {
    try {
      // 读取mock JSON文件
      ClassPathResource resource = new ClassPathResource("mock/flights-mock.json");
      JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
      JsonNode dataNode = rootNode.get("data");
      
      if (dataNode == null || !dataNode.isArray()) {
        log.warn("Mock data format is invalid, returning empty list");
        return new ArrayList<>();
      }
      
      List<FlightOfferDto> mockFlights = new ArrayList<>();
      
      // 解析每个航班offer
      for (JsonNode offerNode : dataNode) {
        FlightOfferDto dto = parseMockFlightOffer(offerNode, request);
        if (dto != null) {
          mockFlights.add(dto);
        }
      }
      
      log.info("Returned {} mock flight offers for request: {} -> {}", 
               mockFlights.size(), request.getOrigin(), request.getDestination());
      
      return mockFlights;
      
    } catch (IOException e) {
      log.error("Failed to read mock flights data", e);
      return new ArrayList<>();
    }
  }

  /**
   * 解析单个Mock航班offer并替换请求参数
   */
  private FlightOfferDto parseMockFlightOffer(JsonNode offerNode, FlightOffersSearchRequest request) {
    try {
      FlightOfferDto dto = new FlightOfferDto();
      
      // 设置基本属性
      dto.setType(getStringValue(offerNode, "type"));
      dto.setId(getStringValue(offerNode, "id"));
      dto.setSource(getStringValue(offerNode, "source"));
      dto.setInstantTicketingRequired(getBooleanValue(offerNode, "instantTicketingRequired"));
      dto.setDisablePricing(getBooleanValue(offerNode, "disablePricing"));
      dto.setNonHomogeneous(getBooleanValue(offerNode, "nonHomogeneous"));
      dto.setOneWay(getBooleanValue(offerNode, "oneWay"));
      dto.setPaymentCardRequired(getBooleanValue(offerNode, "paymentCardRequired"));
      dto.setLastTicketingDate(getStringValue(offerNode, "lastTicketingDate"));
      dto.setNumberOfBookableSeats(getIntValue(offerNode, "numberOfBookableSeats"));
      
      // 解析价格信息
      JsonNode priceNode = offerNode.get("price");
      if (priceNode != null) {
        SearchPriceDto priceDto = new SearchPriceDto();
        priceDto.setCurrency(getStringValue(priceNode, "currency"));
        priceDto.setTotal(getStringValue(priceNode, "total"));
        dto.setPrice(priceDto);
      }
      
      // 解析行程信息并替换参数
      JsonNode itinerariesNode = offerNode.get("itineraries");
      if (itinerariesNode != null && itinerariesNode.isArray()) {
        List<ItineraryDto> itineraryDtos = new ArrayList<>();
        
        for (JsonNode itineraryNode : itinerariesNode) {
          ItineraryDto itineraryDto = parseItinerary(itineraryNode, request);
          if (itineraryDto != null) {
            itineraryDtos.add(itineraryDto);
          }
        }
        
        dto.setItineraries(itineraryDtos);
      }
      
      return dto;
      
    } catch (Exception e) {
      log.error("Failed to parse mock flight offer", e);
      return null;
    }
  }

  /**
   * 解析行程信息并替换出发地、目的地、日期
   */
  private ItineraryDto parseItinerary(JsonNode itineraryNode, FlightOffersSearchRequest request) {
    ItineraryDto itineraryDto = new ItineraryDto();
    itineraryDto.setDuration(getStringValue(itineraryNode, "duration"));
    
    JsonNode segmentsNode = itineraryNode.get("segments");
    if (segmentsNode != null && segmentsNode.isArray()) {
      List<SearchSegmentDto> segmentDtos = new ArrayList<>();
      
      for (int i = 0; i < segmentsNode.size(); i++) {
        JsonNode segmentNode = segmentsNode.get(i);
        SearchSegmentDto segmentDto = parseSegment(segmentNode, request, i);
        if (segmentDto != null) {
          segmentDtos.add(segmentDto);
        }
      }
      
      itineraryDto.setSegments(segmentDtos);
    }
    
    return itineraryDto;
  }

  /**
   * 解析航段信息并替换机场代码和日期
   */
  private SearchSegmentDto parseSegment(JsonNode segmentNode, FlightOffersSearchRequest request, int segmentIndex) {
    SearchSegmentDto segmentDto = new SearchSegmentDto();
    
    segmentDto.setId(getStringValue(segmentNode, "id"));
    segmentDto.setCarrierCode(getStringValue(segmentNode, "carrierCode"));
    segmentDto.setNumber(getStringValue(segmentNode, "number"));
    segmentDto.setDuration(getStringValue(segmentNode, "duration"));
    segmentDto.setNumberOfStops(getIntValue(segmentNode, "numberOfStops"));
    segmentDto.setBlacklistedInEU(getBooleanValue(segmentNode, "blacklistedInEU"));
    
    // 解析出发信息并替换参数
    JsonNode departureNode = segmentNode.get("departure");
    if (departureNode != null) {
      AirportInfoDto departure = new AirportInfoDto();
      
      // 替换出发机场代码
      String departureCode = segmentIndex == 0 ? request.getOrigin() : getStringValue(departureNode, "iataCode");
      departure.setIataCode(departureCode);
      departure.setTerminal(getStringValue(departureNode, "terminal"));
      
      // 替换出发日期（保持时间不变）
      String originalDateTime = getStringValue(departureNode, "at");
      String newDateTime = replaceDateInDateTime(originalDateTime, request.getDepartureDate());
      departure.setAt(newDateTime);
      
      segmentDto.setDeparture(departure);
    }
    
    // 解析到达信息并替换参数
    JsonNode arrivalNode = segmentNode.get("arrival");
    if (arrivalNode != null) {
      AirportInfoDto arrival = new AirportInfoDto();
      
      // 替换到达机场代码
      String arrivalCode = segmentIndex == 0 ? request.getDestination() : getStringValue(arrivalNode, "iataCode");
      arrival.setIataCode(arrivalCode);
      arrival.setTerminal(getStringValue(arrivalNode, "terminal"));
      
      // 替换到达日期（保持时间不变）
      String originalDateTime = getStringValue(arrivalNode, "at");
      String targetDate = segmentIndex == 0 ? request.getDepartureDate() : 
                         (request.getReturnDate() != null ? request.getReturnDate() : request.getDepartureDate());
      String newDateTime = replaceDateInDateTime(originalDateTime, targetDate);
      arrival.setAt(newDateTime);
      
      segmentDto.setArrival(arrival);
    }
    
    return segmentDto;
  }

  /**
   * 替换日期时间字符串中的日期部分，保持时间不变
   */
  private String replaceDateInDateTime(String originalDateTime, String newDate) {
    if (originalDateTime == null || newDate == null) {
      return originalDateTime;
    }
    
    try {
      // 解析原始日期时间
      LocalDateTime originalDT = LocalDateTime.parse(originalDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      
      // 解析新日期
      LocalDate newLocalDate = LocalDate.parse(newDate, DateTimeFormatter.ISO_LOCAL_DATE);
      
      // 组合新日期和原始时间
      LocalDateTime newDT = LocalDateTime.of(newLocalDate, originalDT.toLocalTime());
      
      return newDT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      
    } catch (Exception e) {
      log.warn("Failed to replace date in datetime: {} -> {}, returning original", originalDateTime, newDate);
      return originalDateTime;
    }
  }

  // 辅助方法：安全获取字符串值
  private String getStringValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
  }

  // 辅助方法：安全获取布尔值
  private boolean getBooleanValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() && fieldNode.asBoolean();
  }

  // 辅助方法：安全获取整数值
  private int getIntValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() ? fieldNode.asInt() : 0;
  }

}
