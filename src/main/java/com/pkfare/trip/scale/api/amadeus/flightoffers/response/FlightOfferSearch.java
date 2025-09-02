package com.pkfare.trip.scale.api.amadeus.flightoffers.response;



import lombok.Data;

@Data
public class FlightOfferSearch {

  private String type;
  private String id;
  private String source;
  private boolean instantTicketingRequired;
  private boolean disablePricing;
  private boolean nonHomogeneous;
  private boolean oneWay;
  private boolean paymentCardRequired;
  private String lastTicketingDate;
  private int numberOfBookableSeats;
  private Itinerary[] itineraries;
  private SearchPrice price;
  //private PricingOptions pricingOptions;
  //private String[] validatingAirlineCodes;
  // private TravelerPricing[] travelerPricings;
  // private String choiceProbability;
  //private FareRules fareRules;

}
