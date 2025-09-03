package com.pkfare.trip.scale.api.amadeus.hotelbycity.response;

import lombok.Data;

@Data
public class HotelAddressDto {
  private String category;
  private String[] lines;
  private String postalCode;
  private String countryCode;
  private String cityName;
  private String stateCode;
  private String postalBox;
  private String text;
  private String state;
}
