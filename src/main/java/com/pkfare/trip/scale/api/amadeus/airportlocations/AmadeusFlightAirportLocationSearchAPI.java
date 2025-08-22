package com.pkfare.trip.scale.api.amadeus.airportlocations;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Location;
import com.pkfare.trip.scale.api.amadeus.airportlocations.request.FlightAirportLocationSearchRequest;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusFlightAirportLocationSearchAPI {

  public Location[] queryFlightLocation(FlightAirportLocationSearchRequest request) {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("subType", request.getSubType())
        .and("keyword", request.getKeyword());

    try {
      Location[] locations = amadeus.referenceData.locations.get(params);
      if (locations == null || locations.length == 0) {
        log.error("call AmadeusFlightAirportLocationSearchAPI return empty，resonse:{} ", locations);
        return locations;
      }
      if (locations[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightAirportLocationSearchAPI failed，resonse：{}", locations[0].getResponse());
        throw new AmadeusApiException(locations[0].getResponse().getStatusCode(), locations[0].getResponse().getResult().toString());
      }
      return locations;
    } catch (Exception e) {
      log.error("call AmadeusFlightAirportLocationSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightOffersSearchAPI failed");
    }

  }

}
