package com.pkfare.trip.scale.api.amadeus.airportlocations.response;

import com.amadeus.resources.Location.Address;
import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import lombok.Data;

@Data
public class LocationDto {
  private String type;
  private String subType;
  private String name;
  private String detailedName;
  private String timeZoneOffset;
  private String iataCode;
  private GeoCodeDto geoCode;
  private Address address;
  //private Distance distance;
  //private Analytics analytics;
  private Double relevance;

}
