package com.pkfare.trip.scale.api.amadeus.flightoffers.request;

import lombok.Data;

@Data
public class FlightOffersSearchRequest {
  private String origin;           // 出发地IATA代码 (必需)，城市/airPort
  private String destination;      // 目的地IATA代码 (必需)，城市/airPort
  private String departureDate;    // 出发日期 YYYY-MM-DD (必需)
  private String returnDate;       // 返回日期 YYYY-MM-DD (必需)
  private int adults;
  private int children;
  private int infants;
  private Boolean nonStop;         // 是否只显示直飞航班 (可选)
  private String currency;     // 航班提供的首选货币。货币以ISO 4217格式指定，例如EUR表示欧元
  private int maxPrice;          // 定义每个返回报价的价格限制。该值应该是一个正数，没有小数
  private Integer max;             // 往返航班的最大数量。如果指定，该值应大于或等于1
}
