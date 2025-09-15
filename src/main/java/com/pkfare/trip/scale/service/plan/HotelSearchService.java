package com.pkfare.trip.scale.service.plan;

import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelInfoDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.OfferDto;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.EstimatedRoomType;
import com.pkfare.trip.scale.plan.service.response.HotelDetail;
import com.pkfare.trip.scale.plan.service.response.HotelOffer;
import com.pkfare.trip.scale.plan.service.response.HotelPrice;
import com.pkfare.trip.scale.plan.service.response.QualifiedFreeText;
import com.pkfare.trip.scale.plan.service.response.RoomDetails;
import com.pkfare.trip.scale.plan.service.response.SegmentInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusHotelService;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.PriceUtil;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

/**
 * 酒店搜索服务
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class HotelSearchService {

  @Autowired
  private AmadeusHotelService amadeusHotelService;

  private static final String DEFAULT_PRICE_RANGE = "10-5000";
  private static final int DEFAULT_RADIUS = 10;
  private static final String DEFAULT_RADIUS_UNIT = "KM";

  /**
   * 搜索酒店
   *
   * @param param   搜索参数
   * @param flights 航班信息（用于确定入住时间）
   * @return 酒店信息列表
   */
  public List<HotelInfo> searchHotels(GeneratePlanParam param, List<FlightInfo> flights, Map<String, List<String>> localHotelIdMap) {
    log.info("Searching hotels for {} routes", param.getTrip_routes().size());

    // 1. 计算入住和退房时间
    Map<String, LocalDate[]> checkInOutDates = calculateCheckInOutDates(param, flights);

    // 2. 逐段查询最优报价
    List<HotelInfo> hotels = param.getTrip_routes().parallelStream()
        .filter(route -> {
          List<String> hotelIds = localHotelIdMap.get(route.getLocation_code());
          LocalDate[] dates = checkInOutDates.get(route.getLocation_code());
          return hotelIds != null && !hotelIds.isEmpty() && dates != null;
        })
        .flatMap(route -> {
          List<String> hotelIds = localHotelIdMap.get(route.getLocation_code());
          LocalDate[] dates = checkInOutDates.get(route.getLocation_code());

          List<String> limitedHotelIds = hotelIds;// hotelIds.subList(0, Math.min(hotelIds.size(), 20));
          List<HotelInfo> routeHotels = searchHotelOffers(param, route, limitedHotelIds, dates[0], dates[1]);

          log.debug("Found {} hotels for route {}", routeHotels.size(), route.getLocation_code());
          return routeHotels.stream();
        })
        .collect(Collectors.toList());

    log.info("Found {} hotels in total", hotels.size());
    return hotels;
  }

  /**
   * 根据城市获取酒店ID列表
   *
   * @param routes 行程路线列表
   * @return 城市代码与酒店ID列表的映射
   */
  public Map<String, List<String>> getHotelsByCity(List<TripRouteParam> routes) {
    log.info("Getting hotels by city for {} routes", routes.size());

    Map<String, List<String>> localHotelIdMap = new HashMap<>();

    routes.parallelStream().forEach(route -> {
      String locationCode = route.getLocation_code();

      QueryHotelByCityRequest request = new QueryHotelByCityRequest();
      request.setCityCode(locationCode);
      request.setRadius(DEFAULT_RADIUS);
      request.setRadiusUnit(DEFAULT_RADIUS_UNIT);

      try {
        List<HotelInfoDto> hotels = amadeusHotelService.searchHotelsByCity(request);

        if (hotels != null && hotels.size() > 0) {
          List<String> hotelIds = hotels.stream()
              .map(HotelInfoDto::getHotelId)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

          localHotelIdMap.put(locationCode, hotelIds);
          log.info("Found {} hotels in city {}", hotelIds.size(), locationCode);
        } else {
          log.warn("No hotels found in city {}", locationCode);
          localHotelIdMap.put(locationCode, new ArrayList<>());
        }
      } catch (Exception e) {
        log.error("Failed to search hotels in city {}", locationCode, e);
        localHotelIdMap.put(locationCode, new ArrayList<>());
      }
    });

    return localHotelIdMap;
  }

  /**
   * 计算入住和退房日期
   *
   * @param param   参数
   * @param flights 航班信息
   * @return 城市代码与入住退房日期的映射
   */
  private Map<String, LocalDate[]> calculateCheckInOutDates(GeneratePlanParam param, List<FlightInfo> flights) {
    Map<String, LocalDate[]> checkInOutDates = new HashMap<>();

    // 获取去程到达时间作为第一个城市的入住时间
    LocalDate firstCheckInDate = getArrivalDate(flights, true);
    LocalDate lastCheckOutDate = getReturnDate(flights);
    if (firstCheckInDate == null) {
      // 如果无法从航班信息获取，使用开始日期
      firstCheckInDate = DateUtil.parseDate(param.getStart_period());
    }

    LocalDate currentCheckInDate = firstCheckInDate;

    for (TripRouteParam route : param.getTrip_routes()) {
      LocalDate checkOutDate = DateUtil.addDays(currentCheckInDate, route.getStay_days());
      if(checkOutDate.isAfter(lastCheckOutDate)){
        log.info("calculateCheckInOutDates force update date :checkOutDate:{},lastCheckOutDate:{}",checkOutDate,lastCheckOutDate);
        checkOutDate = lastCheckOutDate;
      }

      checkInOutDates.put(route.getLocation_code(),
          new LocalDate[]{currentCheckInDate, checkOutDate});

      log.info("calculateCheckInOutDates City {}: CheckIn={}, CheckOut={}",
          route.getLocation_code(), currentCheckInDate, checkOutDate);

      // 下一个城市的入住时间是当前城市的退房时间
      currentCheckInDate = checkOutDate;

    }

    log.info("calculateCheckInOutDates:{}",checkInOutDates);
    return checkInOutDates;
  }

  /**
   * 从航班信息中获取到达日期
   *
   * @param flights    航班信息
   * @param isOutbound 是否去程
   * @return 到达日期
   */
  private LocalDate getArrivalDate(List<FlightInfo> flights, boolean isOutbound) {
    if (flights == null || flights.isEmpty()) {
      return null;
    }

    for (FlightInfo flight : flights) {
      if (flight.getItineraries() != null && !flight.getItineraries().isEmpty()) {
        // 取第一个航班第一个行程的的到达时间
        var itinerary = flight.getItineraries().get(0);
        if (itinerary.getSegments() != null && !itinerary.getSegments().isEmpty()) {
          // 取最后一个航段的到达时间
          SegmentInfo lastSegment = itinerary.getSegments().get(
              itinerary.getSegments().size() - 1);

          if (lastSegment.getArrivalTime() != null) {
            try {
              // 解析到达时间字符串为LocalDate
              LocalDateTime arrivalDateTime = LocalDateTime.parse(
                  lastSegment.getArrivalTime(),
                  DateTimeFormatter.ISO_LOCAL_DATE_TIME);
              return arrivalDateTime.toLocalDate();
            } catch (Exception e) {
              log.warn("Failed to parse arrival time: {}", lastSegment.getArrivalTime());
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * 从航班信息中获取返程日期
   *
   * @param flights 航班信息列表
   * @return 返程日期
   */
  private LocalDate getReturnDate(List<FlightInfo> flights) {
    if (flights == null || flights.isEmpty()) {
      return null;
    }

    for (FlightInfo flight : flights) {
      if (flight.getItineraries() != null && flight.getItineraries().size() >= 2) {
        // 对于往返航班，返程是第二个行程
        var returnItinerary = flight.getItineraries().get(1);
        if (returnItinerary.getSegments() != null && !returnItinerary.getSegments().isEmpty()) {
          // 取返程最后一个航段的到达时间
          SegmentInfo lastSegment = returnItinerary.getSegments().get(
              returnItinerary.getSegments().size() - 1);

          if (lastSegment.getArrivalTime() != null) {
            try {
              // 解析到达时间字符串为LocalDate
              LocalDateTime arrivalDateTime = LocalDateTime.parse(
                  lastSegment.getArrivalTime(),
                  DateTimeFormatter.ISO_LOCAL_DATE_TIME);
              return arrivalDateTime.toLocalDate();
            } catch (Exception e) {
              log.warn("Failed to parse return arrival time: {}", lastSegment.getArrivalTime());
            }
          }
        }
      } else if (flight.getItineraries() != null && flight.getItineraries().size() == 1) {
        // 如果只有一个行程，检查是否为单程航班
        if (Boolean.TRUE.equals(flight.getOneWay())) {
          // 单程航班，返回去程到达日期作为返程日期的参考
          var itinerary = flight.getItineraries().get(0);
          if (itinerary.getSegments() != null && !itinerary.getSegments().isEmpty()) {
            SegmentInfo lastSegment = itinerary.getSegments().get(
                itinerary.getSegments().size() - 1);

            if (lastSegment.getArrivalTime() != null) {
              try {
                LocalDateTime arrivalDateTime = LocalDateTime.parse(
                    lastSegment.getArrivalTime(),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                // 对于单程航班，可以假设返程日期为到达日期后的几天
                return arrivalDateTime.toLocalDate().plusDays(7); // 假设停留7天
              } catch (Exception e) {
                log.warn("Failed to parse single trip arrival time: {}", lastSegment.getArrivalTime());
              }
            }
          }
        }
      }
    }

    // 如果无法从航班信息中获取返程日期，返回null
    log.warn("Unable to determine return date from flight information");
    return null;
  }

  /**
   * 搜索酒店报价
   *
   * @param param    参数
   * @param route    路线
   * @param hotelIds 酒店ID列表
   * @param checkIn  入住日期
   * @param checkOut 退房日期
   * @return 酒店信息列表
   */
  private List<HotelInfo> searchHotelOffers(GeneratePlanParam param, TripRouteParam route,
      List<String> hotelIds, LocalDate checkIn, LocalDate checkOut) {
    log.info("Searching hotel offers for city {} with {} hotels", route.getLocation_code(), hotelIds.size());

    HotelOffersSearchRequest request = new HotelOffersSearchRequest();
    request.setHotelIds(hotelIds);
    request.setCheckInDate(DateUtil.formatDate(checkIn));
    request.setCheckOutDate(DateUtil.formatDate(checkOut));
    request.setAdults(param.getAdult_number() + param.getChild_number());
    request.setCountryOfResidence(route.getCountry_code());
    request.setRoomQuantity(param.getRoom_quantity());
    request.setPriceRange(DEFAULT_PRICE_RANGE);
    request.setCurrency(param.getCurrency());

    try {
      List<HotelOfferDto> offers = amadeusHotelService.searchHotelOffers(request);

      if (offers != null && offers.size() > 0) {
        List<HotelInfo> hotelInfos = filterCheapestHotels(offers, route, checkIn, checkOut);
        log.info("Found {} hotel offers for city {}", hotelInfos.size(), route.getLocation_code());
        return hotelInfos;
      } else {
        log.warn("No hotel offers found for city {}", route.getLocation_code());
      }
    } catch (Exception e) {
      log.error("Failed to search hotel offers for city {}", route.getLocation_code(), e);
    }

    return new ArrayList<>();
  }

  /**
   * 筛选最便宜的酒店
   *
   * @param offers   酒店报价列表
   * @param route    路线
   * @param checkIn  入住日期
   * @param checkOut 退房日期
   * @return 筛选后的酒店信息列表
   */
  private List<HotelInfo> filterCheapestHotels(List<HotelOfferDto> offers, TripRouteParam route,
      LocalDate checkIn, LocalDate checkOut) {
    if (offers == null || offers.isEmpty()) {
      return new ArrayList<>();
    }

    // 按价格排序，选择最便宜的
    List<HotelOfferDto> cheapestOfferList = offers.stream()
        .sorted(Comparator.comparing(offer -> {
          if (offer.getOffers() != null && offer.getOffers().size() > 0) {
            return PriceUtil.parsePrice(offer.getOffers().get(0).getPrice().getTotal());
          }
          return BigDecimal.valueOf(Double.MAX_VALUE);
        })).collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(cheapestOfferList)) {
      return convertToHotelInfo(cheapestOfferList, route, checkIn, checkOut);
    }

    return new ArrayList<>();
  }

  /**
   * 转换为HotelInfo
   *
   * @param hotelOfferSearchList    酒店报价
   * @param route    路线
   * @param checkIn  入住日期
   * @param checkOut 退房日期
   * @return 酒店信息
   */
  private List<HotelInfo> convertToHotelInfo(List<HotelOfferDto> hotelOfferSearchList, TripRouteParam route, LocalDate checkIn,
      LocalDate checkOut) {
    List<HotelInfo> hotelInfoList = Lists.newArrayList();
    for (int i = 0; i < hotelOfferSearchList.size(); i++) {
      hotelInfoList.add(buildHotelInfo(hotelOfferSearchList.get(i), route.getLocation_code(), route.getDestination_city(), i));
    }
    return hotelInfoList;
  }

  /**
   * 构建酒店信息对象（6参数版本）
   *
   * @param offer 酒店报价DTO
   * @param cityCode 城市代码
   * @param cityName 城市名称
   * @param checkIn 入住日期
   * @param checkOut 退房日期
   * @param index 索引（用于确定是否为首选）
   * @return 酒店信息对象
   */
  public static HotelInfo buildHotelInfo(HotelOfferDto offer, String cityCode, String cityName, 
                                         int index) {
    if (offer == null || offer.getHotel() == null) {
      return null;
    }

    HotelInfo hotelInfo = new HotelInfo();
    hotelInfo.setType("hotel-offers");
    hotelInfo.setAvailable(true);
    hotelInfo.setPreferred(index == 0);

    // 构建酒店详情
    HotelDetail hotelDetail = buildHotelDetail(offer, cityCode, cityName);
    hotelInfo.setHotel(hotelDetail);

    // 构建酒店报价列表
    List<HotelOffer> hotelOffers = buildHotelOffers(offer);
    hotelInfo.setOffers(hotelOffers);

    return hotelInfo;
  }

  /**
   * 构建酒店详情对象
   *
   * @param offer 酒店报价DTO
   * @param cityCode 城市代码
   * @param cityName 城市名称
   * @return 酒店详情对象
   */
  private static HotelDetail buildHotelDetail(HotelOfferDto offer, String cityCode, String cityName) {
    HotelDetail hotelDetail = new HotelDetail();
    
    if (offer.getHotel() != null) {
      hotelDetail.setType(offer.getHotel().getType());
      hotelDetail.setHotelId(offer.getHotel().getHotelId());
      hotelDetail.setChainCode(offer.getHotel().getChainCode());
      hotelDetail.setBrandCode(offer.getHotel().getBrandCode());
      hotelDetail.setDupeId(offer.getHotel().getDupeId());
      hotelDetail.setName(offer.getHotel().getName());
      hotelDetail.setCityCode(cityCode);
      hotelDetail.setCityName(cityName);
      hotelDetail.setLatitude(offer.getHotel().getLatitude());
      hotelDetail.setLongitude(offer.getHotel().getLongitude());
    }
    
    return hotelDetail;
  }

  /**
   * 构建酒店报价列表
   *
   * @param offer 酒店报价DTO
   * @return 酒店报价列表
   */
  private static List<HotelOffer> buildHotelOffers(HotelOfferDto offer) {
    List<HotelOffer> hotelOffers = new ArrayList<>();
    
    if (offer.getOffers() != null && !offer.getOffers().isEmpty()) {
      for (OfferDto offerDto : offer.getOffers()) {
        HotelOffer hotelOffer = new HotelOffer();
        
        hotelOffer.setType(offerDto.getType());
        hotelOffer.setId(offerDto.getId());
        hotelOffer.setCheckInDate(offerDto.getCheckInDate());
        hotelOffer.setCheckOutDate(offerDto.getCheckOutDate());
        hotelOffer.setRoomQuantity(offerDto.getRoomQuantity());
        hotelOffer.setRateCode(offerDto.getRateCode());
        hotelOffer.setCategory(offerDto.getCategory());
        
        // 构建房间详情
        if (offerDto.getRoom() != null) {
          RoomDetails roomDetails = new RoomDetails();
          roomDetails.setType(offerDto.getRoom().getType());
          
          if (offerDto.getRoom().getTypeEstimated() != null) {
            EstimatedRoomType estimatedRoomType = new EstimatedRoomType();
            estimatedRoomType.setBedType(offerDto.getRoom().getTypeEstimated().getBedType());
            estimatedRoomType.setBeds(offerDto.getRoom().getTypeEstimated().getBeds());
            estimatedRoomType.setCategory(offerDto.getRoom().getTypeEstimated().getCategory());
            roomDetails.setTypeEstimated(estimatedRoomType);
          }
          
          if (offerDto.getRoom().getDescription() != null) {
            QualifiedFreeText description = new QualifiedFreeText();
            description.setLang(offerDto.getRoom().getDescription().getLang());
            description.setText(offerDto.getRoom().getDescription().getText());
            roomDetails.setDescription(description);
          }
          
          hotelOffer.setRoom(roomDetails);
        }
        
        // 构建价格信息
        if (offerDto.getPrice() != null) {
          HotelPrice hotelPrice = new HotelPrice();
          hotelPrice.setCurrency(offerDto.getPrice().getCurrency());
          hotelPrice.setTotal(offerDto.getPrice().getTotal());
          hotelPrice.setBase(offerDto.getPrice().getBase());
          hotelPrice.setSellingTotal(offerDto.getPrice().getSellingTotal());
          hotelOffer.setPrice(hotelPrice);
        }
        
        hotelOffers.add(hotelOffer);
      }
    }
    
    return hotelOffers;
  }

  /**
   * 构建地址字符串
   *
   * @param address 地址对象
   * @return 地址字符串
   */
  private static String buildAddressString(Object address) {
    // 这里需要根据实际的Address对象结构来实现
    // 简化实现
    return address != null ? address.toString() : "";
  }
}
