package com.pkfare.trip.scale.api.amadeus.airportlocations.request;

import com.google.common.collect.Lists;
import  java.util.List;
import lombok.Data;

@Data
public class FlightAirportLocationSearchRequest {

  private List<String> subType = Lists.newArrayList("AIRPORT");

  private String keyword;


}
