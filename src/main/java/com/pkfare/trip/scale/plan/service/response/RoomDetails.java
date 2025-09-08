package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class RoomDetails {

  private String type;
  private EstimatedRoomType typeEstimated;
  private QualifiedFreeText description;

}
