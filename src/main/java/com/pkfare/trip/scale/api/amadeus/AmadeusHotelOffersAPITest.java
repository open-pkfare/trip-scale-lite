package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.AmadeusHotelOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.response.HotelOfferDto;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusHotelOffersAPITest {

  @Configuration
  @ComponentScan(basePackages = "com.pkfare.trip.scale.api.amadeus")
  static class TestConfig {
  }

  public static void main(String[] args) throws ResponseException {
    // 测试/v3/shopping/hotel-offers
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
      AmadeusHotelOffersSearchAPI searchAPI = context.getBean(AmadeusHotelOffersSearchAPI.class);
      List<HotelOfferDto> response = searchAPI.hotelOffersSearch(buildHotelOffersSearch());
      log.info("response : {}",response);
    }
  }

  private static HotelOffersSearchRequest buildHotelOffersSearch() {
    HotelOffersSearchRequest request = new HotelOffersSearchRequest();
    request.setHotelIds(Lists.newArrayList("MCLONGHM","HNPARKGU"));
    request.setAdults(1);
    request.setCheckInDate("2025-10-02");
    request.setCheckOutDate("2025-10-05");
    request.setCountryOfResidence("FR");
    request.setRoomQuantity(1);
    request.setPriceRange("200-10000");
    request.setCurrency("EUR");
    request.setPaymentPolicy("NONE");
    return request;
  }

}
