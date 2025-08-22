package com.pkfare.trip.scale.api.amadeus.hoteloffers.request;

import java.util.List;
import lombok.Data;

@Data
public class HotelOffersSearchRequest {

  private List<String> hotelIds;
  private int adults;
  private String checkInDate;    // 入住日期（酒店当地日期） Format YYYY-MM-DD
  private String checkOutDate;   // 入住退房日期（酒店当地日期） Format YYYY-MM-DD
  private String countryOfResidence; // 旅客居住国代码，使用ISO 3166-1格式表示。
  private int roomQuantity;   // Number of rooms requested (1-9).
  private String priceRange; // 按每晚价格间隔过滤酒店报价（例如：200-300或-300或100）。
  private String currency;
  private String paymentPolicy = "NONE";
  private Boolean bestRateOnly = true; // 用于只返回每个酒店或所有可提供的最便宜的报价。

}
