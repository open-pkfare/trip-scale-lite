package com.pkfare.trip.scale.api.amadeus.flightoffers.response;



import java.util.List;
import lombok.Data;

@Data
public class FlightOfferDto {

  private String type;
  private String id;
  private String source;
  private Boolean instantTicketingRequired;
  private Boolean disablePricing;
  private Boolean nonHomogeneous;
  private Boolean oneWay;
  private Boolean paymentCardRequired;
  private String lastTicketingDate;
  private Integer numberOfBookableSeats;
  private List<ItineraryDto> itineraries;
  private SearchPriceDto price;
  //private PricingOptions pricingOptions;
  //private String[] validatingAirlineCodes;
  // private TravelerPricing[] travelerPricings;
  // private String choiceProbability;
  //private FareRules fareRules;

}
