package com.pkfare.trip.scale.dto;

import java.util.List;
import lombok.Data;

@Data
public class TripDemand {

  private List<String> must_go_destinations;
  private int passenger_number;
  private String origin;
  private String origin_country_code;
  private int days;
  private String budgets;
  private String currency;
  private String brief;
  private String start_period;
  private String end_period;

}
