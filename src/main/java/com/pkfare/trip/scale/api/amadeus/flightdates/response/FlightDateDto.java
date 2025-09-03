package com.pkfare.trip.scale.api.amadeus.flightdates.response;

import java.util.Date;
import lombok.Data;

@Data
public class FlightDateDto {
  private String type;
  private String origin;
  private String destination;
  private Date departureDate;
  private Date returnDate;
  private FlightDatePriceDto price;
}
