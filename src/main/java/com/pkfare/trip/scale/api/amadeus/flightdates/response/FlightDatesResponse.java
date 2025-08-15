package com.pkfare.trip.scale.api.amadeus.flightdates.response;


import java.util.List;
import lombok.Data;

@Data
public class FlightDatesResponse {
  private List<FlightDate> data;
  private Meta meta;

}
