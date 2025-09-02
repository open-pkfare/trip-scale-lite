package com.pkfare.trip.scale.plan.service.param;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdjustFlightParam {
  /**
   * 调整项类型: flight, hotel, activity
   *
   * @see AdjustItemEnum
   */
  private String item;

  /**
   * 调整项ID
   */
  private String id;

  /**
   * 调整类型: replace, advance, delay, cheaper
   * @see FlightAdjustTypeEnum
   */
  private String adjustType;

  /**
   * 航班不经停
   */
  private boolean noStop;

  /**
   * 时间变更（小时），航班调整时使用（暂未实现）
   */
  private Integer timeChange;

  /**
   * 最高价格
   */
  private Double maxPrice;
}
