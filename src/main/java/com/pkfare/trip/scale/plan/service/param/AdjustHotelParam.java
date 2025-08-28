package com.pkfare.trip.scale.plan.service.param;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdjustHotelParam {
  /**
   * 调整项类型: flight, hotel, activity
   *
   * @see AdjustItemEnum
   */
  private String item;
  /**
   * 调整项ID
   */
  private String hotelId;
  /**
   * 酒店星级，1-5
   */
  private List<String> ratings;
  /**
   * 酒店偏好偏好
   */
  private List<String> amenities;
  /**
   * 房间数量，1-9
   */
  private int roomQuantity;
  /**
   * 最高价格
   */
  private String maxPrice;
}
