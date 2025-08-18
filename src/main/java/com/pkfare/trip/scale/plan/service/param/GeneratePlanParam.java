package com.pkfare.trip.scale.plan.service.param;

import lombok.Data;

/**
 * 生成plan的入参
 */
@Data
public class GeneratePlanParam {

  private String origin;           // 出发地IATA代码 (必需)，城市/airPort
  private String destination;      // 目的地IATA代码 (必需)，城市/airPort
  private String destinationCountry; // 旅客居住国代码，使用ISO 3166-1格式表示。
  private String departureDate;    // 出发日期 YYYY-MM-DD (必需)
  private String returnDate;       // 返回日期 YYYY-MM-DD (必需)
  private String duration;         // 如果没有具体的返回时间，旅行的确切时间或范围，以天为单位。当“oneWay”为true时，不能设置该参数。范围用逗号指定，并且是包含的，例如2,8
  private int adults;
  private int children;
  private int infants;
  private Boolean nonStop;         // 是否只显示直飞航班 (可选) ？
  private int maxPrice;          // 预算金额。该值应该是一个正数，没有小数
  private String currency;       // 预算金额币种。货币以ISO 4217格式指定，例如EUR表示欧元

}
