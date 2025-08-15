package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.FlightDate;

public class Test {


  public static void main(String[] args) throws ResponseException {
    Amadeus amadeus = Amadeus.builder(System.getenv()).build();
    Params params = Params.with("origin", "MAD");
    FlightDate[] flightDates = amadeus.shopping.flightDates.get(params);
  }

}
