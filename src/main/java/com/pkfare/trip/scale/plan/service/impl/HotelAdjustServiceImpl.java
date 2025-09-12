package com.pkfare.trip.scale.plan.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByGeocodeRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelInfoDto;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.AdjustHotelParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.CityHotelsInfo;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusHotelService;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import com.pkfare.trip.scale.util.PriceUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 酒店调整服务实现类
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class HotelAdjustServiceImpl implements TripPlanAdjustInterface {

  public static final Integer DEFAULT_RADIUS = 1;
  @Autowired
  private AmadeusHotelService amadeusHotelService;
  @Autowired
  private GoogleAiService googleAiService;
  @Autowired
  private ObjectMapper objectMapper;

  /**
   * 调整某个城市的酒店
   *
   * @param generatePlanParam 生成计划参数
   * @param tripPlan          原始旅行计划
   * @param adjustParam       调整参数列表
   * @return 调整后的旅行计划
   */
  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonNode adjustParam) {
    AdjustHotelParam adjustHotelParam = objectMapper.convertValue(adjustParam, AdjustHotelParam.class);
    log.info("Adjusting hotel param: {}", adjustHotelParam);
    boolean found = false;
    CityHotelsInfo cityHotelsInfo = null;
    List<CityHotelsInfo> cityHotelsInfos = tripPlan.getCityHotelsInfos();
    for (int i = 0; i < cityHotelsInfos.size(); i++) {
      cityHotelsInfo = cityHotelsInfos.get(i);
      if (!cityHotelsInfo.getPreferredHotel().getHotel().getHotelId().equals(adjustHotelParam.getId())) {
        continue;
      }
      List<HotelInfo> hotelInfos = searchHotel(generatePlanParam, cityHotelsInfo.getPreferredHotel(), adjustHotelParam);
      cityHotelsInfo.setPreferredHotel(hotelInfos.getFirst());
      cityHotelsInfo.setAlternativeHotels(hotelInfos.subList(1, hotelInfos.size()));
      found = true;
      log.info("Hotel adjusted successfully: old={}, new={}", adjustHotelParam.getId(), cityHotelsInfo.getPreferredHotel().getHotel().getHotelId());
      break;
    }
    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }

    List<DailyRoutePlan> dailyPlans = tripPlan.getDailyPlans();
    for (int i = 0; i < dailyPlans.size(); i++) {
      DailyRoutePlan dailyRoutePlan = dailyPlans.get(i);
      HotelInfo hotel = dailyRoutePlan.getPreferredHotel();
      if (hotel.getHotel().getHotelId().equals(adjustHotelParam.getId())) {
        dailyRoutePlan.setPreferredHotel(cityHotelsInfo.getPreferredHotel());
        dailyRoutePlan.setAlternativeHotels(cityHotelsInfo.getAlternativeHotels());
        try {
          googleAiService.generateRoutesOptimized(dailyRoutePlan, dailyRoutePlan.getActivities());
        } catch (Exception e) {
          throw new TripPlanException(TripPlanErrorCodeEnum.OPTIMIZE_HOTEL_FAILED, e);
        }
        log.info("Hotel adjusted successfully: {}", dailyRoutePlan.getPreferredHotel().getHotel().getHotelId());
        break;
      }
    }
  }

  private List<HotelInfo> searchHotel(GeneratePlanParam generatePlanParam, HotelInfo oldHotel, AdjustHotelParam adjustHotelParam) {
    QueryHotelByGeocodeRequest geocodeRequest = new QueryHotelByGeocodeRequest();
    geocodeRequest.setLatitude(oldHotel.getHotel().getLatitude());
    geocodeRequest.setLongitude(oldHotel.getHotel().getLongitude());
    geocodeRequest.setRadius(DEFAULT_RADIUS);
    geocodeRequest.setRadiusUnit("KM");
    geocodeRequest.setRatings(adjustHotelParam.getHotelRatings());
    geocodeRequest.setAmenities(adjustHotelParam.getHotelAmenities());
    geocodeRequest.setCityCode(oldHotel.getHotel().getCityCode());
    List<HotelInfoDto> hotels;
    do {
      hotels = amadeusHotelService.searchHotelsByGeocode(geocodeRequest);
      if (hotels != null && !hotels.isEmpty()) {
        break;
      }
      geocodeRequest.setRadius(geocodeRequest.getRadius() + 1);
    } while (geocodeRequest.getRadius() <= 5);
    if (hotels == null || hotels.isEmpty()) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }
    // 不能重复之前酒店
    List<String> hotelIds = hotels.stream()
        .map(HotelInfoDto::getHotelId)
        .filter(hotelId -> !hotelId.equals(oldHotel.getHotel().getHotelId())).toList();
    if (hotelIds.isEmpty()) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }

    String countryCode = hotels.getFirst().getAddress().getCountryCode();
    HotelOffersSearchRequest request = new HotelOffersSearchRequest();
    request.setHotelIds(hotelIds);
    request.setCheckInDate(oldHotel.getOffers().getFirst().getCheckInDate());
    request.setCheckOutDate(oldHotel.getOffers().getFirst().getCheckOutDate());
    request.setAdults(generatePlanParam.getAdult_number() + generatePlanParam.getChild_number());
    request.setCountryOfResidence(countryCode);
    request.setRoomQuantity(1);
    BigDecimal maxPrice = adjustHotelParam.getMaxPrice();
    if (Objects.isNull(maxPrice) || maxPrice.compareTo(BigDecimal.ZERO) <= 0) {
      maxPrice = PriceUtil.parsePrice(oldHotel.getOffers().get(0).getPrice().getTotal());
    }
    request.setPriceRange("1-" + maxPrice.toString());
    request.setCurrency(oldHotel.getOffers().get(0).getPrice().getCurrency());
    try {
      List<HotelOfferDto> offers = amadeusHotelService.searchHotelOffers(request);
      if (offers == null || offers.isEmpty()) {
        throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
      }

      // 按价格升序排序，选择前几个
      List<HotelOfferDto> dtoList = offers.stream()
          .filter(offer -> !offer.getHotel().getHotelId().equals(oldHotel.getHotel().getHotelId()))
          .sorted(Comparator.comparing(offer -> {
            if (offer.getOffers() != null && !offer.getOffers().isEmpty()) {
              return PriceUtil.parsePrice(offer.getOffers().getFirst().getPrice().getTotal());
            }
            return BigDecimal.valueOf(Double.MAX_VALUE);
          })).limit(5).toList();

      List<HotelInfo> hotelInfos = new ArrayList<>();
      String cityCode = oldHotel.getHotel().getCityCode();
      String cityName = oldHotel.getHotel().getCityName();
      int index = 0;
      for (HotelOfferDto offerDto : dtoList) {
        hotelInfos.add(HotelSearchService.buildHotelInfo(offerDto, cityCode, cityName, index++));
      }
      return hotelInfos;
    } catch (Exception e) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }
  }
}