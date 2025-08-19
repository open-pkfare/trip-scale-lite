package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.FlightDate;
import com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusFlightDatesAPITest {


  public static void main(String[] args) throws ResponseException {
    // 测试/v1/shopping/flight-dates 接口
    AmadeusFlightDatesAPI amadeusFlightDatesAPI = new AmadeusFlightDatesAPI();
    FlightDate[] response = amadeusFlightDatesAPI.flightDates(buildFlightDatesRequest());
    log.info("response : {}", response);

  }

  private static FlightDatesRequest buildFlightDatesRequest() {
    FlightDatesRequest request = new FlightDatesRequest();
    request.setOrigin("PAR");
    request.setDestination("LIS");
    request.setDepartureDate("2025-10-01");
    request.setDuration("2,8");
    request.setOneWay(false);
    request.setNonStop(true);
    request.setMaxPrice(10000);
    return request;
  }

}
