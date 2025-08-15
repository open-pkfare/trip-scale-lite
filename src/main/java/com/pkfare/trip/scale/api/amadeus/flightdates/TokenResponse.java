package com.pkfare.trip.scale.api.amadeus.flightdates;


import lombok.Data;

@Data
public class TokenResponse {
  private String accessToken;
  private String tokenType;
  private Integer expiresIn;
}
