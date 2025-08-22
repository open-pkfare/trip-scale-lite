package com.pkfare.trip.scale.api.amadeus.flightdates;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightDate;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusFlightDatesAPI {

  public FlightDate[] flightDates(FlightDatesRequest flightDatesRequest) {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("origin", flightDatesRequest.getOrigin()).and("destination", flightDatesRequest.getDestination())
        .and("departureDate", flightDatesRequest.getDepartureDate())
        .and("duration", flightDatesRequest.getDuration())
        .and("oneWay", flightDatesRequest.getOneWay()).and("nonStop", flightDatesRequest.getNonStop())
        .and("maxPrice", flightDatesRequest.getMaxPrice());

    try {
      FlightDate[] flightDates = amadeus.shopping.flightDates.get(params);
      if (flightDates == null || flightDates.length == 0) {
        log.error("call AmadeusFlightDatesAPI return empty，resonse:{} ", flightDates);
        return flightDates;
      }
      if (flightDates[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightDatesAPI failed，resonse：{}", flightDates[0].getResponse());
        throw new AmadeusApiException(flightDates[0].getResponse().getStatusCode(), flightDates[0].getResponse().getResult().toString());
      }
      return flightDates;
    } catch (Exception e) {
      log.error("call AmadeusFlightDatesAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightDatesAPI failed");
    }

  }

}
