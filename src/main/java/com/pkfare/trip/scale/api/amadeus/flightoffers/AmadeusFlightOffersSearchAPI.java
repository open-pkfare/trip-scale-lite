package com.pkfare.trip.scale.api.amadeus.flightoffers;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.FlightOfferSearch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


@Slf4j
public class AmadeusFlightOffersSearchAPI {

  private final ObjectMapper mapper = new ObjectMapper();
  private static final String FLIGHT_OFFERS_ENDPOINT = "/v2/shopping/flight-offers";

  public FlightOfferSearch[] flightOffersSearch(FlightOffersSearchRequest flightOffersSearchRequest) {
    Amadeus amadeus = AmadeusClient.get();
    String travelClass = flightOffersSearchRequest.getTravelClass();
    Params params = Params.with("originLocationCode", flightOffersSearchRequest.getOrigin())
        .and("destinationLocationCode", flightOffersSearchRequest.getDestination())
        .and("departureDate", flightOffersSearchRequest.getDepartureDate())
        .and("adults", flightOffersSearchRequest.getAdults()).and("children", flightOffersSearchRequest.getChildren())
        .and("travelClass", StringUtils.isBlank(travelClass) ? "ECONOMY" : travelClass)
        .and("infants", flightOffersSearchRequest.getInfants()).and("nonStop", flightOffersSearchRequest.getNonStop())
        .and("currencyCode", flightOffersSearchRequest.getCurrency()).and("maxPrice", flightOffersSearchRequest.getMaxPrice())
        .and("max", flightOffersSearchRequest.getMax());
    if(!Objects.isNull(flightOffersSearchRequest.getReturnDate())){
      params.and("returnDate", flightOffersSearchRequest.getReturnDate());
    }
    try {
      //String result = amadeus.get(FLIGHT_OFFERS_ENDPOINT, params).getBody();
      //FlightOfferSearch[] flightOffers = parseResponse(result);

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

  /**
  public FlightOfferSearch[] parseResponse(String jsonResponse) {
    try {
      JsonNode rootNode = mapper.readTree(jsonResponse);

      if (rootNode.has("data") && rootNode.get("data").isArray()) {
        JsonNode dataArray = rootNode.get("data");
        FlightOfferSearch[] flightOffers = new FlightOfferSearch[dataArray.size()];
        for (int i = 0; i < dataArray.size(); i++) {
          JsonNode offerNode = dataArray.get(i);
          FlightOfferSearch flightOffer = new FlightOfferSearch();
          flightOffers[i] = flightOffer;
          flightOffer.setOneWay(offerNode.has("oneWay") ? offerNode.get("oneWay").asBoolean() : false);
          // Price information
          if (offerNode.has("price")) {
            JsonNode priceNode = offerNode.get("price");
            SearchPrice flightPrice = new SearchPrice();
            flightPrice.setTotal(priceNode.has("total") ? priceNode.get("total").asText() : "0");
            flightPrice.setCurrency(priceNode.has("currency") ? priceNode.get("currency").asText() : "EUR");
            flightOffer.setPrice(flightPrice);
          }

          // Itineraries
          if (offerNode.has("itineraries") && offerNode.get("itineraries").isArray()) {
            JsonNode itineraries = offerNode.get("itineraries");
            Itinerary[] itinerariesArr = new Itinerary[itineraries.size()];
            flightOffer.setItineraries(itinerariesArr);
            for (int j = 0; j < itineraries.size(); j++) {
              JsonNode itineraryNode = itineraries.get(j);
              Itinerary itinerary = new Itinerary();
              itinerariesArr[j] = itinerary;

              if (itineraryNode.has("segments") && itineraryNode.get("segments").isArray()) {
                JsonNode segments = itineraryNode.get("segments");
                SearchSegment[] segmentsArr = new SearchSegment[segments.size()];
                itinerary.setSegments(segmentsArr);
                for (int k = 0; k < segments.size(); k++) {
                  JsonNode segmentNode = segments.get(k);
                  SearchSegment segment = new SearchSegment();
                  segmentsArr[k] = segment;
                  if (segmentNode.has("departure") && segmentNode.has("arrival")) {
                    JsonNode departureNode = segmentNode.get("departure");
                    JsonNode arrivalNode = segmentNode.get("arrival");
                    AirportInfo departure = new AirportInfo();
                    AirportInfo arrival = new AirportInfo();
                    segment.setDeparture(departure);
                    segment.setArrival(arrival);
                    departure.setIataCode(departureNode.has("iataCode") ? departureNode.get("iataCode").asText() : "N/A");
                    departure.setAt(departureNode.has("at") ? departureNode.get("at").asText() : "N/A");
                    arrival.setIataCode(arrivalNode.has("iataCode") ? arrivalNode.get("iataCode").asText() : "N/A");
                    arrival.setAt(arrivalNode.has("at") ? arrivalNode.get("at").asText() : "N/A");
                    if (segmentNode.has("carrierCode")) {
                      segment.setCarrierCode(segmentNode.get("carrierCode").asText());
                      segment.setNumber(segmentNode.has("number") ? segmentNode.get("number").asText() : "");
                    }
                  }
                }
              }
            }
          }
        }
        return flightOffers;
      } else {
        log.error("No flight offers found in the response.response: {} ", jsonResponse);
        throw new AmadeusApiException(500, "No flight offers found");
      }

    } catch (Exception e) {
      log.error("Error parsing or printing results: ", e);
      throw new AmadeusApiException(500, "query flight offers faild");
    }
  }
   **/

}
