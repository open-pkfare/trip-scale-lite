package com.pkfare.trip.scale.plan.service.param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdjustHotelParam {
  /**
   * 必填，调整项类型: flight, hotel, activity
   *
   * @see AdjustItemEnum
   */
  private String item;
  /**
   * 日期
   */
  private LocalDate date;
  /**
   * 调整项ID，必填
   */
  private String id;
  /**
   * 酒店星级，1-5
   */
  private List<String> hotelRatings;
  /**
   * 酒店偏好
   * @see com.pkfare.trip.scale.model.enums.HotelAmenityEnum
   */
  private List<String> hotelAmenities;
  /**
   * 房间数量，1-9
   */
  private int hotelRoomQuantity;
  /**
   * 最高价格，最低价格
   */
  private BigDecimal maxPrice;
}
