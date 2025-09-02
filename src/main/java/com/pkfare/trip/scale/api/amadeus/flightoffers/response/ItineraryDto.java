package com.pkfare.trip.scale.api.amadeus.flightoffers.response;


import java.util.List;
import lombok.Data;

@Data
public class ItineraryDto {
  private String duration;
  private List<SearchSegmentDto> segments;

}
