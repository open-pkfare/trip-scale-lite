package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class HotelOffer {
  private String type;
  private String id;
  private String checkInDate;
  private String checkOutDate;
  private Integer roomQuantity;
  private String rateCode;
  private String category;
  private RoomDetails room;
  //private Guests guests;
  private HotelPrice price;

}
