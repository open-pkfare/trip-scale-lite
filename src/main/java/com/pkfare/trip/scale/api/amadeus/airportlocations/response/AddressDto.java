package com.pkfare.trip.scale.api.amadeus.airportlocations.response;


import lombok.Data;

@Data
public class AddressDto {

  private String cityName;
  private String cityCode;
  private String countryName;
  private String countryCode;
  private String regionCode;

}
