package com.pkfare.trip.scale.api.amadeus.hoteloffers.response;

import lombok.Data;

@Data
public class RoomDetailsDto {

  private String type;
  private EstimatedRoomTypeDto typeEstimated;
  private QualifiedFreeTextDto description;


}
