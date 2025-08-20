package com.pkfare.trip.scale.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.pkfare.trip.scale.gson.deserializer.DateDeserializer;
import java.util.Date;

public class GsonGlobalConfig {

  // 全局 Gson 实例（线程安全）
  private static final Gson INSTANCE;

  static {
    // 注册全局反序列化器（可同时注册多个）
    INSTANCE = new GsonBuilder()
        .registerTypeAdapter(Date.class, new DateDeserializer()) // 日期
        .setDateFormat("yyyy-MM-dd HH:mm:ss") // 可选：设置默认日期格式（仅对未注册适配器的类型生效）
        .create();
  }

  // 私有构造防止外部实例化
  private GsonGlobalConfig() {}

  // 获取全局实例
  public static Gson getGson() {
    return INSTANCE;
  }
}
