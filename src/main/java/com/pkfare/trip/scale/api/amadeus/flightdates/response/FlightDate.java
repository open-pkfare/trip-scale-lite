package com.pkfare.trip.scale.api.amadeus.flightdates.response;


import lombok.Data;

@Data
public class FlightDate {
  private String type;
  private String origin;
  private String destination;
  private String departureDate;
  private String returnDate;
  private Price price;
  private String airline;

}
