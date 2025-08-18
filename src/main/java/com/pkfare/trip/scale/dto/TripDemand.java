package com.pkfare.trip.scale.dto;

import java.util.List;
import lombok.Data;

@Data
public class TripDemand {

  private List<String> mustGoDestinations;
  private int passengerNumber;
  private String origin;
  private int days;
  private String budgets;

}
