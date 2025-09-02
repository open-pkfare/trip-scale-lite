package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;

import lombok.Data;

@Data
public class OfferDto {
  private String type;
  private String id;
  private String checkInDate;
  private String checkOutDate;
  private Integer roomQuantity;
  private String rateCode;
  private String category;
  private QualifiedFreeTextDto description;
  //private RoomDetails room;
  //private Guests guests;
  private HotelPriceDto price;
  //private PolicyDetails policies;
  //private String self;

}
