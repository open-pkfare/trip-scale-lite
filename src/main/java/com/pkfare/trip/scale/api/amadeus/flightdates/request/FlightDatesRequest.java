package com.pkfare.trip.scale.api.amadeus.flightdates.request;


import lombok.Data;

@Data
public class FlightDatesRequest {
  private String origin;           // 出发地IATA代码 (必需)
  private String destination;      // 目的地IATA代码 (必需)
  private String departureDate;    // 出发日期 YYYY-MM-DD (必需)
  private String returnDate;       // 返回日期 YYYY-MM-DD (可选)
  private String currency;         // 货币代码 (可选)
  private Integer max;             // 最大结果数 (可选)
  private Boolean nonStop;         // 是否只显示直飞航班 (可选)
  private Boolean oneWay;          // 是否单程 (可选)
}
