package com.pkfare.trip.scale.plan.service.impl;

import com.amadeus.resources.Hotel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByGeocodeRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.AdjustHotelParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusHotelService;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.PriceUtil;
import java.math.BigDecimal;
import java.util.Arrays;
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

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripRoutePlanResult tripPlan, JsonNode adjustParam) {
    AdjustHotelParam adjustHotelParam = objectMapper.convertValue(adjustParam, AdjustHotelParam.class);
    log.info("Adjusting hotel param: {}", adjustHotelParam);

    List<DailyRoutePlan> dailyPlans = tripPlan.getDailyPlans();
    boolean found = false;
    for (int i = 0; i < dailyPlans.size(); i++) {
      DailyRoutePlan dailyRoutePlan = dailyPlans.get(i);
      HotelInfo hotel = dailyRoutePlan.getPreferredHotel();
      if (hotel.getHotelId().equals(adjustHotelParam.getId())) {
        found = true;
        HotelInfo newHotel = searchHotel(generatePlanParam, hotel, adjustHotelParam);
        if (Objects.isNull(newHotel)) {
          throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
        }
        dailyRoutePlan.setPreferredHotel(newHotel);

        try {
          googleAiService.generateRoutes(dailyRoutePlan, dailyRoutePlan.getActivities());
        } catch (Exception e) {
          throw new TripPlanException(TripPlanErrorCodeEnum.OPTIMIZE_HOTEL_FAILED, e);
        }
        log.info("Hotel adjusted successfully: {}", dailyRoutePlan.getPreferredHotel().getHotelId());
        break;
      }
    }
    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }
  }

  private HotelInfo searchHotel(GeneratePlanParam generatePlanParam, HotelInfo oldHotel, AdjustHotelParam adjustHotelParam) {
    QueryHotelByGeocodeRequest geocodeRequest = new QueryHotelByGeocodeRequest();
    geocodeRequest.setLatitude(oldHotel.getLatitude());
    geocodeRequest.setLongitude(oldHotel.getLongitude());
    geocodeRequest.setRadius(DEFAULT_RADIUS);
    geocodeRequest.setRadiusUnit("KM");
    geocodeRequest.setRatings(adjustHotelParam.getHotelRatings());
    geocodeRequest.setAmenities(adjustHotelParam.getHotelAmenities());
    Hotel[] hotels;
    do {
      hotels = amadeusHotelService.searchHotelsByGeocode(geocodeRequest);
      if (hotels != null && hotels.length != 0) {
        break;
      }
      geocodeRequest.setRadius(geocodeRequest.getRadius() + 1);
    } while (geocodeRequest.getRadius() <= 5);
    if (hotels == null || hotels.length == 0) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }

    List<String> hotelIds = Arrays.stream(hotels).map(Hotel::getHotelId).toList();
    String countryCode = hotels[0].getAddress().getCountryCode();
    HotelOffersSearchRequest request = new HotelOffersSearchRequest();
    request.setHotelIds(hotelIds);
    request.setCheckInDate(DateUtil.formatDate(oldHotel.getCheckInDate()));
    request.setCheckOutDate(DateUtil.formatDate(oldHotel.getCheckOutDate()));
    request.setAdults(generatePlanParam.getAdult_number() + generatePlanParam.getChild_number());
    request.setCountryOfResidence(countryCode);
    request.setRoomQuantity(generatePlanParam.getRoom_quantity());
    BigDecimal maxPrice = adjustHotelParam.getMaxPrice();
    if (Objects.isNull(maxPrice) || maxPrice.compareTo(BigDecimal.ZERO) <= 0) {
      maxPrice = oldHotel.getTotalPrice();
    }
    request.setPriceRange("1-" + maxPrice.toString());
    request.setCurrency(oldHotel.getCurrency());
    try {
      List<HotelOfferDto> offers = amadeusHotelService.searchHotelOffers(request);
      if (offers == null || offers.isEmpty()) {
        throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
      }

      // 按价格排序，选择最便宜的
      HotelOfferDto offerDto = offers.stream().min(Comparator.comparing(offer -> {
        if (offer.getOffers() != null && !offer.getOffers().isEmpty()) {
          return PriceUtil.parsePrice(offer.getOffers().getFirst().getPrice().getTotal());
        }
        return BigDecimal.valueOf(Double.MAX_VALUE);
      })).orElse(null);

      return HotelSearchService.buildHotelInfo(offerDto, oldHotel.getCityCode(), oldHotel.getCityName(), oldHotel.getCheckInDate(),
          oldHotel.getCheckOutDate(), 0);
    } catch (Exception e) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }
  }
}