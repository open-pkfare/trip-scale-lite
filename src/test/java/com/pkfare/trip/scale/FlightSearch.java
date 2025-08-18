package com.pkfare.trip.scale;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.FlightDate;
import com.amadeus.resources.FlightDestination;

public class FlightSearch {

  static Amadeus amadeus = Amadeus.builder("dfYr0PQs3GMwRFZTZzGlmR3lp6Gj6gjD", "fGS2iBdaPJpcG9SB").build();

  public static void main(String[] args) throws ResponseException {
    flightDestinations();
    //    flightDates();
  }

  private static void flightDestinations() throws ResponseException {
    Params params = Params.with("origin", "PAR").and("maxPrice", 200);
    FlightDestination[] flightDestinations = amadeus.shopping.flightDestinations.get(params);
    if (flightDestinations[0].getResponse().getStatusCode() != 200) {
      System.out.println("Wrong status code for Flight Inspiration Search: " + flightDestinations[0].getResponse().getStatusCode());
      return;
    }
    System.out.println(flightDestinations[0]);
  }

  private static void flightDates() throws ResponseException {
    Params params = Params.with("origin", "HKG").and("destination", "SIN").and("departureDate", "2025-09-01");
    FlightDate[] flightDates = amadeus.shopping.flightDates.get(params);
    if (flightDates[0].getResponse().getStatusCode() != 200) {
      System.out.println("Wrong status code for Flight Inspiration Search: " + flightDates[0].getResponse().getStatusCode());
      return;
    }
    System.out.println(flightDates[0]);
  }
}
