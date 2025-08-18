package com.pkfare.trip.scale.api.amadeus.flightdates.request;


import lombok.Data;

@Data
public class FlightDatesRequest {
  private String origin;           // 出发地IATA代码 (必需)
  private String destination;      // 目的地IATA代码 (必需)
  private String departureDate;    // 出发日期 YYYY-MM-DD (必需)
  private Boolean oneWay;          // 是否单程 (可选)
  private String duration;         // 旅行的确切时间或范围，以天为单位。当“oneWay”为true时，不能设置该参数。范围用逗号指定，并且是包含的，例如2,8
  private Boolean nonStop;         // 是否只显示直飞航班 (可选)
  private int maxPrice;          // 定义每个返回报价的价格限制。该值应该是一个正数，没有小数

  private String currency;         // 货币代码 (可选)
  private Integer max;             // 最大结果数 (可选)


}
