package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;


import lombok.Data;

@Data
public class EstimatedRoomTypeDto {
  private String category;
  private Integer beds;
  private String bedType;

}
