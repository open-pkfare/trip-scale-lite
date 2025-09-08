package com.pkfare.trip.scale.plan.service.param;

import java.util.List;
import lombok.Data;

/**
 * 生成plan的入参
 */
@Data
public class GeneratePlanParam {

  private String origin;
  private String location_code;
  private String  start_period;
  private String  end_period;
  private int trip_days;
  private int adult_number;
  private int child_number;
  //private int infant_number;
  private String budgets;
  private String currency;
  private int room_quantity;
  private List<TripRouteParam> trip_routes;

}
