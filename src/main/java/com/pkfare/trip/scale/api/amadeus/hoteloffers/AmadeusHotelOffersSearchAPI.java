package com.pkfare.trip.scale.api.amadeus.hoteloffers;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.HotelOfferSearch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.EstimatedRoomTypeDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.OfferDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelPriceDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.QualifiedFreeTextDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.RoomDetailsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AmadeusHotelOffersSearchAPI {

  @Value("${amadeus.hotel.offers.mock.enabled:true}")
  private boolean mockEnabled;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<HotelOfferDto> hotelOffersSearch(HotelOffersSearchRequest hotelOffersSearchRequest) {
    if (needMock(hotelOffersSearchRequest)) {
      return mockApiResponse(hotelOffersSearchRequest);
    }
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("hotelIds", hotelOffersSearchRequest.getHotelIds())
        .and("adults", hotelOffersSearchRequest.getAdults())
        .and("checkInDate", hotelOffersSearchRequest.getCheckInDate()).and("checkOutDate", hotelOffersSearchRequest.getCheckOutDate())
        .and("countryOfResidence", hotelOffersSearchRequest.getCountryOfResidence()).and("roomQuantity", hotelOffersSearchRequest.getRoomQuantity())
        .and("priceRange", hotelOffersSearchRequest.getPriceRange()).and("currency", hotelOffersSearchRequest.getCurrency())
        .and("bestRateOnly", hotelOffersSearchRequest.getBestRateOnly()).and("paymentPolicy", hotelOffersSearchRequest.getPaymentPolicy());

    try {
      HotelOfferSearch[] hotelOffers = amadeus.shopping.hotelOffersSearch.get(params);
      if (hotelOffers == null || hotelOffers.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotelOffers);
        return Lists.newArrayList();
      }
      if (hotelOffers[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotelOffers[0].getResponse());
        throw new AmadeusApiException(hotelOffers[0].getResponse().getStatusCode(), hotelOffers[0].getResponse().getResult().toString());
      }
      return convert2Dtos(hotelOffers);
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }

  }

  /**
   * 判断是否需要使用Mock数据
   * 先判断开关是否打开，然后判断是否存在对应countryOfResidence-roomQuantity-hotelId的mock文件
   */
  private boolean needMock(HotelOffersSearchRequest hotelOffersSearchRequest) {
    if (!mockEnabled) {
      return false;
    }
    
    // 检查是否存在对应的mock文件（任意一个hotelId匹配即可）
    if (hotelOffersSearchRequest.getHotelIds() != null && !hotelOffersSearchRequest.getHotelIds().isEmpty()) {
      for (String hotelId : hotelOffersSearchRequest.getHotelIds()) {
        String mockFilePath = buildMockFilePath(hotelOffersSearchRequest.getCountryOfResidence(), 
                                                hotelOffersSearchRequest.getRoomQuantity(), hotelId);
        try {
          ClassPathResource resource = new ClassPathResource(mockFilePath);
          boolean exists = resource.exists();
          if (exists) {
            log.debug("Found mock file for countryOfResidence: {}, roomQuantity: {}, hotelId: {} - path: {}", 
                      hotelOffersSearchRequest.getCountryOfResidence(), hotelOffersSearchRequest.getRoomQuantity(), 
                      hotelId, mockFilePath);
            return true;
          }
        } catch (Exception e) {
          log.debug("Failed to check mock file for hotelId {}: {}", hotelId, e.getMessage());
        }
      }
    }
    
    log.debug("No mock file found for any hotelId in request: {}", hotelOffersSearchRequest.getHotelIds());
    return false;
  }
  
  /**
   * 构建mock文件路径
   */
  private String buildMockFilePath(String countryOfResidence, int roomQuantity, String hotelId) {
    return String.format("mock/hotels/%s-%d-%s.json", countryOfResidence, roomQuantity, hotelId);
  }

  /**
   * 返回Mock API响应
   * 根据countryOfResidence-roomQuantity-hotelId读取对应的mock文件
   */
  private List<HotelOfferDto> mockApiResponse(HotelOffersSearchRequest request) {
    long startTime = System.currentTimeMillis();
    List<HotelOfferDto> allMockHotels = new ArrayList<>();
    
    // 遍历所有hotelIds，查找对应的mock文件
    if (request.getHotelIds() != null && !request.getHotelIds().isEmpty()) {
      for (String hotelId : request.getHotelIds()) {
        String mockFilePath = buildMockFilePath(request.getCountryOfResidence(), 
                                                request.getRoomQuantity(), hotelId);
        
        try {
          ClassPathResource resource = new ClassPathResource(mockFilePath);
          if (!resource.exists()) {
            log.debug("Mock file not found for hotelId: {} at path: {}", hotelId, mockFilePath);
            continue;
          }
          
          // 读取并解析mock JSON文件
          JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
          JsonNode dataNode = rootNode.get("data");
          
          if (dataNode == null || !dataNode.isArray()) {
            log.warn("Mock hotel data format is invalid for hotelId: {}, skipping", hotelId);
            continue;
          }
          
          // 解析每个酒店offer并替换日期
          for (JsonNode hotelNode : dataNode) {
            HotelOfferDto dto = parseMockHotelOffer(hotelNode, request);
            if (dto != null) {
              allMockHotels.add(dto);
            }
          }
          
          log.debug("Loaded {} mock hotel offers from file: {}", dataNode.size(), mockFilePath);
          
        } catch (IOException e) {
          log.error("Failed to read mock hotel file for hotelId: {} at path: {}", hotelId, mockFilePath, e);
        } catch (Exception e) {
          log.error("Failed to parse mock hotel data for hotelId: {}", hotelId, e);
        }
      }
    }
    
    long duration = System.currentTimeMillis() - startTime;
    log.info("Returned {} mock hotel offers for checkIn: {}, checkOut: {}, hotelIds: {} in {} ms",
        allMockHotels.size(), request.getCheckInDate(), request.getCheckOutDate(), 
        request.getHotelIds(), duration);

    return allMockHotels;
  }

  /**
   * 解析单个Mock酒店offer并替换日期参数
   */
  private HotelOfferDto parseMockHotelOffer(JsonNode hotelNode, HotelOffersSearchRequest request) {
    try {
      HotelOfferDto dto = new HotelOfferDto();

      // 设置基本属性
      dto.setType(getStringValue(hotelNode, "type"));
      dto.setAvailable(getBooleanValue(hotelNode, "available"));
      dto.setSelf(getStringValue(hotelNode, "self"));

      // 解析酒店信息
      JsonNode hotelInfoNode = hotelNode.get("hotel");
      if (hotelInfoNode != null) {
        HotelDto hotelDto = parseHotelInfo(hotelInfoNode);
        dto.setHotel(hotelDto);
      }

      // 解析offers信息并替换日期
      JsonNode offersNode = hotelNode.get("offers");
      if (offersNode != null && offersNode.isArray()) {
        List<OfferDto> offerDtos = new ArrayList<>();

        for (JsonNode offerNode : offersNode) {
          OfferDto offerDto = parseOffer(offerNode, request);
          if (offerDto != null) {
            offerDtos.add(offerDto);
          }
        }

        dto.setOffers(offerDtos);
      }

      return dto;

    } catch (Exception e) {
      log.error("Failed to parse mock hotel offer", e);
      return null;
    }
  }

  /**
   * 解析酒店基本信息
   */
  private HotelDto parseHotelInfo(JsonNode hotelInfoNode) {
    HotelDto hotelDto = new HotelDto();
    hotelDto.setType(getStringValue(hotelInfoNode, "type"));
    hotelDto.setHotelId(getStringValue(hotelInfoNode, "hotelId"));
    hotelDto.setChainCode(getStringValue(hotelInfoNode, "chainCode"));
    hotelDto.setBrandCode(getStringValue(hotelInfoNode, "brandCode"));
    hotelDto.setDupeId(getStringValue(hotelInfoNode, "dupeId"));
    hotelDto.setName(getStringValue(hotelInfoNode, "name"));
    hotelDto.setCityCode(getStringValue(hotelInfoNode, "cityCode"));
    hotelDto.setLatitude(getDoubleValue(hotelInfoNode, "latitude"));
    hotelDto.setLongitude(getDoubleValue(hotelInfoNode, "longitude"));
    return hotelDto;
  }

  /**
   * 解析单个offer并替换日期
   */
  private OfferDto parseOffer(JsonNode offerNode, HotelOffersSearchRequest request) {
    OfferDto offerDto = new OfferDto();

    offerDto.setType(getStringValue(offerNode, "type"));
    offerDto.setId(getStringValue(offerNode, "id"));

    // 替换checkInDate和checkOutDate
    offerDto.setCheckInDate(request.getCheckInDate());
    offerDto.setCheckOutDate(request.getCheckOutDate());

    offerDto.setRoomQuantity(getIntValue(offerNode, "roomQuantity"));
    offerDto.setRateCode(getStringValue(offerNode, "rateCode"));
    offerDto.setCategory(getStringValue(offerNode, "category"));

    // 解析价格信息
    JsonNode priceNode = offerNode.get("price");
    if (priceNode != null) {
      HotelPriceDto priceDto = new HotelPriceDto();
      priceDto.setCurrency(getStringValue(priceNode, "currency"));
      priceDto.setTotal(getStringValue(priceNode, "total"));
      priceDto.setBase(getStringValue(priceNode, "base"));
      priceDto.setSellingTotal(getStringValue(priceNode, "sellingTotal"));
      offerDto.setPrice(priceDto);
    }


    JsonNode roomNode = offerNode.get("room");
    if(roomNode!=null){
      RoomDetailsDto roomDetailsDto = new  RoomDetailsDto();
      offerDto.setRoom(roomDetailsDto);
      roomDetailsDto.setType(getStringValue(roomNode, "type"));
      JsonNode typeEstimatedNode = roomNode.get("typeEstimated");
      if(typeEstimatedNode!=null){
        EstimatedRoomTypeDto estimatedRoomTypeDto = new EstimatedRoomTypeDto();
        roomDetailsDto.setTypeEstimated(estimatedRoomTypeDto);
        estimatedRoomTypeDto.setBedType(getStringValue(typeEstimatedNode, "bedType"));
        estimatedRoomTypeDto.setBeds(getIntValue(typeEstimatedNode, "beds"));
        estimatedRoomTypeDto.setCategory(getStringValue(typeEstimatedNode, "category"));
      }
      JsonNode descriptionNode = roomNode.get("description");
      if(descriptionNode!=null){
        QualifiedFreeTextDto description = new QualifiedFreeTextDto();
        roomDetailsDto.setDescription(description);
        description.setLang(getStringValue(descriptionNode, "lang"));
        description.setText(getStringValue(descriptionNode, "text"));
      }

    }

    return offerDto;
  }

  /**
   * 转换Amadeus HotelOfferSearch数组为DTO列表
   */
  private List<HotelOfferDto> convert2Dtos(HotelOfferSearch[] hotelOffers) {
    if (hotelOffers == null || hotelOffers.length == 0) {
      return new ArrayList<>();
    }

    List<HotelOfferDto> hotelOfferDtos = new ArrayList<>();

    for (HotelOfferSearch hotelOffer : hotelOffers) {
      if (hotelOffer == null) {
        continue;
      }

      HotelOfferDto dto = new HotelOfferDto();

      // 设置基本属性
      dto.setType(hotelOffer.getType());
      dto.setAvailable(hotelOffer.isAvailable());
      dto.setSelf(hotelOffer.getSelf());

      // 转换酒店信息
      if (hotelOffer.getHotel() != null) {
        HotelDto hotelDto = new HotelDto();
        hotelDto.setType(hotelOffer.getHotel().getType());
        hotelDto.setHotelId(hotelOffer.getHotel().getHotelId());
        hotelDto.setChainCode(hotelOffer.getHotel().getChainCode());
        hotelDto.setBrandCode(hotelOffer.getHotel().getBrandCode());
        hotelDto.setDupeId(hotelOffer.getHotel().getDupeId());
        hotelDto.setName(hotelOffer.getHotel().getName());
        hotelDto.setCityCode(hotelOffer.getHotel().getCityCode());
        hotelDto.setLatitude(hotelOffer.getHotel().getLatitude());
        hotelDto.setLongitude(hotelOffer.getHotel().getLongitude());
        dto.setHotel(hotelDto);
      }

      // 转换offers信息
      if (hotelOffer.getOffers() != null && hotelOffer.getOffers().length > 0) {
        List<OfferDto> offerDtos = new ArrayList<>();

        for (com.amadeus.resources.HotelOfferSearch.Offer offer : hotelOffer.getOffers()) {
          if (offer == null) {
            continue;
          }

          OfferDto offerDto = new OfferDto();
          offerDto.setType(offer.getType());
          offerDto.setId(offer.getId());
          offerDto.setCheckInDate(offer.getCheckInDate());
          offerDto.setCheckOutDate(offer.getCheckOutDate());
          offerDto.setRoomQuantity(offer.getRoomQuantity());
          offerDto.setRateCode(offer.getRateCode());
          offerDto.setCategory(offer.getCategory());

          // 转换价格信息
          if (offer.getPrice() != null) {
            HotelPriceDto priceDto = new HotelPriceDto();
            priceDto.setCurrency(offer.getPrice().getCurrency());
            priceDto.setTotal(offer.getPrice().getTotal());
            priceDto.setBase(offer.getPrice().getBase());
            priceDto.setSellingTotal(offer.getPrice().getSellingTotal());
            offerDto.setPrice(priceDto);
          }
          if (offer.getRoom() != null) {
            RoomDetailsDto roomDetails = new RoomDetailsDto();
            roomDetails.setType(offer.getRoom().getType());
            if(offer.getRoom().getTypeEstimated() !=null){
              EstimatedRoomTypeDto typeEstimated = new EstimatedRoomTypeDto();
              roomDetails.setTypeEstimated(typeEstimated);
              typeEstimated.setCategory(offer.getRoom().getTypeEstimated().getCategory());
              typeEstimated.setBedType(offer.getRoom().getTypeEstimated().getBedType());
              typeEstimated.setBeds(offer.getRoom().getTypeEstimated().getBeds());
            }
            if(offer.getRoom().getDescription() !=null){
              roomDetails.setDescription(roomDetails.getDescription());
              QualifiedFreeTextDto text = new QualifiedFreeTextDto();
              text.setLang(offer.getRoom().getDescription().getLang());
              text.setText(offer.getRoom().getDescription().getText());
            }
          }

          offerDtos.add(offerDto);
        }

        dto.setOffers(offerDtos);
      }

      hotelOfferDtos.add(dto);
    }

    return hotelOfferDtos;
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
  private Integer getIntValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() ? fieldNode.asInt() : null;
  }

  // 辅助方法：安全获取双精度值
  private double getDoubleValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return fieldNode != null && !fieldNode.isNull() ? fieldNode.asDouble() : 0.0;
  }

}
