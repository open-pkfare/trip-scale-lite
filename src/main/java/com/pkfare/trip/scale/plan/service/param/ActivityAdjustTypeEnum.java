package com.pkfare.trip.scale.plan.service.param;

import java.util.Optional;
import lombok.Getter;

/**
 * 活动调整类型枚举
 */
public enum ActivityAdjustTypeEnum {
  REPLACE("replace", "替换"),
  ADD("add", "增加"),
  REDUCE("reduce", "减少"),
  CHEAPER("cheaper", "更便宜的");

  @Getter
  private final String code;

  @Getter
  private final String name;

  ActivityAdjustTypeEnum(String code, String name) {
    this.code = code;
    this.name = name;
  }

  /**
   * 根据code获取枚举值
   *
   * @param code 编码
   * @return 枚举值
   */
  public static Optional<ActivityAdjustTypeEnum> getByCode(String code) {
    for (ActivityAdjustTypeEnum type : values()) {
      if (type.code.equalsIgnoreCase(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}