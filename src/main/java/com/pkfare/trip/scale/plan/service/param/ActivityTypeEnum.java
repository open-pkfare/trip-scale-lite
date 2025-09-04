package com.pkfare.trip.scale.plan.service.param;

/**
 * 活动类型枚举
 * 包含旅游相关的各类活动分类
 */
public enum ActivityTypeEnum {
  /**
   * 历史文化景点、地标、博物馆
   */
  SIGHTS("Historical and cultural attractions, landmarks, museums"),

  /**
   * 户外自然体验、探险旅游
   */
  ACTIVITIES("Outdoor and nature experiences, adventure tours"),

  /**
   * 娱乐、表演、夜生活体验
   */
  NIGHTLIFE("Entertainment, shows, nightlife experiences"),

  /**
   * 主题公园、表演、娱乐场所
   */
  ENTERTAINMENT("Theme parks, shows, entertainment venues"),

  /**
   * 购物体验和旅游
   */
  SHOPPING("Shopping experiences and tours"),

  /**
   * 各类导游服务
   */
  TOURS("Guided tours of all types"),

  /**
   * 交通服务
   */
  TRANSPORT("Transportation services");

  private final String description;

  ActivityTypeEnum(String description) {
    this.description = description;
  }

  /**
   * 获取枚举值的描述信息
   * @return 描述文本
   */
  public String getDescription() {
    return description;
  }
}