package com.pkfare.trip.scale.dto;

import java.util.List;
import lombok.Data;

@Data
public class TripDemand {

  private List<String> must_go_destinations;
  private int passenger_number;
  private String origin;
  private int days;
  private String budgets;
  private String brief;

}
