package com.pkfare.trip.scale.dto;

import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class HistoricalTrip {

  private Duration duration;
  private List<String> destinations;

}
