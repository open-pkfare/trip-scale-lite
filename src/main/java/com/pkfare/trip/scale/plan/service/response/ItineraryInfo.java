package com.pkfare.trip.scale.plan.service.response;

import java.util.List;
import lombok.Data;

/**
 * 行程信息实体
 *
 * @author Trip Scale Team
 */
@Data
public class ItineraryInfo {
  private String id;
  /**
   * 出发地址
   */
  private String departure;

  /**
   * 出发时间
   */
  private String departureTime;

  /**
   * 到达地址
   */
  private String arrival;
    /**
     * 航段列表
     */
    private List<SegmentInfo> segments;
}
