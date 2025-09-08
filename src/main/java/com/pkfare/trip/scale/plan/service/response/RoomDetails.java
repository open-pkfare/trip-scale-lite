package com.pkfare.trip.scale.plan.service.response;

import lombok.Data;

@Data
public class RoomDetails {

  private String type;

  private String category;

  private Integer beds;

  private String bedType;

  /**
   * 描述语言
   */
  private String descriptionLang;

  /**
   * 描述文案
   */
  private String descriptionText;

}
