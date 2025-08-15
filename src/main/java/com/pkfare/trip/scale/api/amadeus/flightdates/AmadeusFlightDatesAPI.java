package com.pkfare.trip.scale.api.amadeus.flightdates;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightDate;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusFlightDatesAPI {

  public FlightDate[] flightDates() {
    Amadeus amadeus = Amadeus.builder(System.getenv()).build();
    Params params = Params.with("origin", "MAD");
    try{
      FlightDate[] flightDates = amadeus.shopping.flightDates.get(params);
      if (flightDates[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightDatesAPI failed，resonse: " + flightDates[0].getResponse());
        throw new AmadeusApiException(flightDates[0].getResponse().getStatusCode(),flightDates[0].getResponse().getResult().toString());
      }
      return flightDates;
    }catch(Exception e){
      log.error("call AmadeusFlightDatesAPI failed", e);
      throw new AmadeusApiException(500,"call AmadeusFlightDatesAPI failed");
    }

  }

}
