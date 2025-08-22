package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.FlightOfferSearch;
import com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusFlightOffersAPITest {


  public static void main(String[] args) throws ResponseException {
    // 测试/v2/shopping/flight-offers 接口
    AmadeusFlightOffersSearchAPI amadeusFlightOffersSearchAPI = new AmadeusFlightOffersSearchAPI();
    FlightOfferSearch[] FlightOffersSearchResponse = amadeusFlightOffersSearchAPI.flightOffersSearch(buildFlightOffersSearch());
    log.info("response : {}",FlightOffersSearchResponse);

  }

  private static FlightOffersSearchRequest buildFlightOffersSearch() {
    FlightOffersSearchRequest request = new FlightOffersSearchRequest();
    request.setOrigin("SYD");
    request.setDestination("BKK");
    request.setDepartureDate("2025-10-02");
    request.setReturnDate("2025-10-06");
    request.setAdults(1);
    request.setChildren(0);
    request.setInfants(0);
    request.setNonStop(true);
    request.setCurrency("EUR");
    request.setMaxPrice(10000);
    request.setMax(250);

    return request;
  }
}
