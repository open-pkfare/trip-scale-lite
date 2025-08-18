package com.pkfare.trip.scale.api.amadeus.flightoffers;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightOfferSearch;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusFlightOffersSearchAPI {

  public FlightOfferSearch[] flightOffersSearch(FlightOffersSearchRequest flightOffersSearchRequest) {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("originLocationCode", flightOffersSearchRequest.getOrigin())
        .and("destinationLocationCode", flightOffersSearchRequest.getDestination())
        .and("departureDate", flightOffersSearchRequest.getDepartureDate()).and("returnDate", flightOffersSearchRequest.getReturnDate())
        .and("adults", flightOffersSearchRequest.getAdults()).and("children", flightOffersSearchRequest.getChildren())
        .and("infants", flightOffersSearchRequest.getInfants()).and("nonStop", flightOffersSearchRequest.getNonStop())
        .and("currencyCode", flightOffersSearchRequest.getCurrency()).and("maxPrice", flightOffersSearchRequest.getMaxPrice())
        .and("max", flightOffersSearchRequest.getMax());

    try {
      FlightOfferSearch[] flightOffers = amadeus.shopping.flightOffersSearch.get(params);
      if (flightOffers == null || flightOffers.length == 0) {
        log.error("call AmadeusFlightOffersSearchAPI return empty，resonse:{} ", flightOffers);
        return flightOffers;
      }
      if (flightOffers[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusFlightOffersSearchAPI failed，resonse：{}", flightOffers[0].getResponse());
        throw new AmadeusApiException(flightOffers[0].getResponse().getStatusCode(), flightOffers[0].getResponse().getResult().toString());
      }
      return flightOffers;
    } catch (Exception e) {
      log.error("call AmadeusFlightOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusFlightOffersSearchAPI failed");
    }

  }

}
