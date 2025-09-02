package com.pkfare.trip.scale.api.amadeus.flightoffers.response;


import lombok.Data;


@Data
public class SearchSegmentDto {
  private AirportInfoDto departure;
  private AirportInfoDto arrival;
  private String carrierCode;
  private String number;
  // private Aircraft aircraft;
  //private OperatingFlight operating;
  private String duration;
  //private FlightStop[] stops;
  private String id;
  private int numberOfStops;
  private boolean blacklistedInEU;


}
