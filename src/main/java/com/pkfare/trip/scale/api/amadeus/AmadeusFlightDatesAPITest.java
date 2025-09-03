package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.FlightDate;
import com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightdates.response.FlightDateDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusFlightDatesAPITest {


  public static void main(String[] args) throws ResponseException {
    // 测试/v1/shopping/flight-dates 接口
    AmadeusFlightDatesAPI amadeusFlightDatesAPI = new AmadeusFlightDatesAPI();
    List<FlightDateDto> response = amadeusFlightDatesAPI.flightDates(buildFlightDatesRequest());
    log.info("response : {}", response);
  }

  private static FlightDatesRequest buildFlightDatesRequest() {
    FlightDatesRequest request = new FlightDatesRequest();
    request.setOrigin("PAR");
    request.setDestination("LIS");
    request.setDepartureDate("2025-09-01,2025-09-30");
    request.setDuration("2,8");
    request.setOneWay(false);
    request.setNonStop(false);
    //request.setMaxPrice(10000);

    // // FlightDate(type=flight-date, origin=PAR, destination=LIS, departureDate=Wed Oct 01 00:00:00 CST 2025, returnDate=Fri Oct 03 00:00:00 CST 2025, price=FlightDate.Price(total=144.23))
    return request;
  }

  private static FlightDatesRequest buildFlightDatesRequest1() {
    FlightDatesRequest request = new FlightDatesRequest();
    request.setOrigin("PAR");
    request.setDestination("LIS");
    request.setDepartureDate("2025-08-22,2025-08-30");
    //request.setReturnDate("2025-08-30");
    //request.setDuration(null);
    request.setOneWay(true);
    request.setNonStop(false);
    //request.setMaxPrice(10000);
    return request;
  }

}
