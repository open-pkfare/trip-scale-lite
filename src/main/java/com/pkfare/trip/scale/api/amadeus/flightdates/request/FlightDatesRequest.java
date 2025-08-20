package com.pkfare.trip.scale.api.amadeus.flightdates.request;


import lombok.Data;

@Data
public class FlightDatesRequest {
  private String origin;           // 出发地IATA代码 (必需)
  private String destination;      // 目的地IATA代码 (必需)
  private String departureDate;    // the date, or range of dates, on which the flight will depart from the origin. Dates are specified in the ISO 8601 YYYY-MM-DD format, e.g. 2017-12-25. Ranges are specified with a comma and are inclusive
  private Boolean oneWay;          // 是否单程 (可选)
  private String duration;         // exact duration or range of durations of the travel, in days. This parameter must not be set if oneWay is true. Ranges are specified with a comma and are inclusive, e.g. 2,8
  private Boolean nonStop = true;         // 是否只显示直飞航班 (可选)
  private int maxPrice;            // 定义每个返回报价的价格限制。该值应该是一个正数，没有小数

  // private String currency;         // 货币代码 (可选)
  //private Integer max;             // 最大结果数 (可选)


}
