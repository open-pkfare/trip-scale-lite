package com.pkfare.trip.scale.api.amadeus.hotelbycity.response;

import com.pkfare.trip.scale.api.amadeus.activities.response.GeoCodeDto;
import lombok.Data;

@Data
public class HotelInfoDto {
  private String subtype;
  private String name;
  private String timeZoneName;
  private String iataCode;
  private HotelAddressDto address;
  private GeoCodeDto geoCode;
  private String googlePlaceId;
  private String openjetAirportId;
  private String uicCode;
  private String hotelId;
  private String chainCode;
  //private Distance distance;
  private String lastUpdate;

}
