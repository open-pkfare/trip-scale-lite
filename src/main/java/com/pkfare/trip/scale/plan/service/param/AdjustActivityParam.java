package com.pkfare.trip.scale.plan.service.param;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * 调整旅行计划参数
 *
 * @author Trip Scale Team
 */
@Data
public class AdjustActivityParam {

  /**
   * 必填，调整项类型: flight, hotel, activity
   *
   * @see AdjustItemEnum
   */
  private String item;

  /**
   * 调整项ID，即Activity id，在调整类型为replace时必填
   */
  private String id;
  /**
   * 日期，必填
   */
  private LocalDate date;

  /**
   * 必填，调整类型: replace, add, reduce, cheaper
   * @see ActivityAdjustTypeEnum
   */
  private String adjustType;

  /**
   * 最高价格
   */
  private BigDecimal maxPrice;

  /**
   * 活动类型，如自然风光、历史建筑等，暂未实现
   */
  private String type;
}