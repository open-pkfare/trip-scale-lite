package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;

import java.util.List;
import lombok.Data;

@Data
public class HotelOfferDto {

  private String type;
  private HotelDto hotel;
  private boolean available;
  private List<OfferDto> offers;
  private String self;

}
