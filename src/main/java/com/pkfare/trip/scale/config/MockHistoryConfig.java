package com.pkfare.trip.scale.config;

import com.google.common.collect.Lists;
import com.pkfare.trip.scale.dto.Focus;
import com.pkfare.trip.scale.dto.HistoricalTrip;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MockHistoryConfig {

    public static List<HistoricalTrip> historicalTrips(){
      return Lists.newArrayList(
          new HistoricalTrip(Duration.between(LocalDate.parse("2020-05-01"),LocalDate.parse("2020-05-07")),Lists.newArrayList("China, Chengdu","China, Chongqing")),
          new HistoricalTrip(Duration.between(LocalDate.parse("2024-02-01"),LocalDate.parse("2020-02-10")),Lists.newArrayList("Vietnam, Hanoi","Vietnam, Da Nang", "Vietnam, ho chi minh"))
      );
    }

  public static List<Focus> recentFocus(){
    return Lists.newArrayList(
        new Focus(LocalDateTime.parse("2025-09-23 19:30:14"),"Italy, Renaissance Artist, Leonardo da Vinci, Michelangelo Buonarroti, Raffaello Santi"),
        new Focus(LocalDateTime.parse("2025-09-23 19:39:14"),"Italy, Tuscan lifestyle"),
        new Focus(LocalDateTime.parse("2025-09-23 20:02:54"),"The history of pizza.")
    );
  }

}
