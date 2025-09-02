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
   * 到达地址
   */
  private String arrival;

    /**
     * 行程时间
     */
    private String duration;
    /**
     * 航段列表
     */
    private List<SegmentInfo> segments;
}
