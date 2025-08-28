package com.pkfare.trip.scale.model.enums;

import java.util.Optional;

public enum HotelAmenityEnum {
  SWIMMING_POOL("swimming_pool", "游泳池"),
  SPA("spa", "水疗中心"),
  FITNESS_CENTER("fitness_center", "健身中心"),
  AIR_CONDITIONING("air_conditioning", "空调"),
  RESTAURANT("restaurant", "餐厅"),
  PARKING("parking", "停车场"),
  PETS_ALLOWED("pets_allowed", "允许携带宠物"),
  AIRPORT_SHUTTLE("airport_shuttle", "机场班车"),
  BUSINESS_CENTER("business_center", "商务中心"),
  DISABLED_FACILITIES("disabled_facilities", "残疾人设施"),
  WIFI("wifi", "WiFi"),
  MEETING_ROOMS("meeting_rooms", "会议室"),
  NO_KID_ALLOWED("no_kid_allowed", "不允许儿童"),
  TENNIS("tennis", "网球场"),
  GOLF("golf", "高尔夫球场"),
  KITCHEN("kitchen", "厨房"),
  ANIMAL_WATCHING("animal_watching", "动物观赏"),
  BABY_SITTING("baby-sitting", "婴儿看护"),
  BEACH("beach", "海滩"),
  CASINO("casino", "赌场"),
  JACUZZI("jacuzzi", "按摩浴缸"),
  SAUNA("sauna", "桑拿"),
  SOLARIUM("solarium", "日光浴室"),
  MASSAGE("massage", "按摩服务"),
  VALET_PARKING("valet_parking", "代客泊车"),
  BAR_LOUNGE("bar_or_lounge", "酒吧或酒廊"),
  KIDS_WELCOME("kids_welcome", "欢迎儿童"),
  NO_PORN_FILMS("no_porn_films", "无成人内容"),
  MINIBAR("minibar", "迷你酒吧"),
  TELEVISION("television", "电视"),
  WI_FI_IN_ROOM("wifi_in_room", "房间内WiFi"),
  ROOM_SERVICE("room_service", "房间服务"),
  GUARDED_PARKING("guarded_parking", "守卫停车场"),
  SERV_SPEC_MENU("serv_spec_menu", "服务特定菜单");

  private final String code;
  private final String name;

  HotelAmenityEnum(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public static Optional<HotelAmenityEnum> getByCode(String code) {
    for (HotelAmenityEnum e : values()) {
      if (e.getCode().equalsIgnoreCase(code)) {
        return Optional.of(e);
      }
    }
    return Optional.empty();
  }
}
