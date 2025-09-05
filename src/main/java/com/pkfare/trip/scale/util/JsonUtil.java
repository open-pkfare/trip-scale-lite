package com.pkfare.trip.scale.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class JsonUtil {

  private static final ObjectMapper INSTANCE = new ObjectMapper();

  static {
    // 核心配置
    INSTANCE.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    INSTANCE.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    INSTANCE.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // 日期格式
    INSTANCE.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    INSTANCE.setTimeZone(TimeZone.getTimeZone("GMT+8"));

    // 模块注册
    INSTANCE.registerModule(new JavaTimeModule());
    INSTANCE.registerModule(new ParameterNamesModule());

    // 性能优化
    //INSTANCE.enable(MapperFeature.OPTIMIZE_PARAM_VETO_COLLECTIONS);
  }

  // 私有构造防止实例化
  private JsonUtil() {}

  /**
   * 序列化对象为JSON字符串
   * @param obj 需序列化的对象
   * @return JSON字符串
   */
  public static String toJson(Object obj) {
    try {
      return INSTANCE.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("JSON序列化失败", e);
    }
  }

  /**
   * 反序列化JSON字符串为指定类型对象
   * @param json JSON字符串
   * @param valueType 目标类型
   * @param <T> 泛型类型
   * @return 反序列化对象
   */
  public static <T> T fromJson(String json, Class<T> valueType) {
    try {
      return INSTANCE.readValue(json, valueType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("JSON反序列化失败", e);
    }
  }

  /**
   * 泛型反序列化（支持复杂类型）
   * @param json JSON字符串
   * @param typeReference 目标类型引用
   * @param <T> 泛型类型
   * @return 反序列化对象
   */
  public static <T> T fromJson(String json, TypeReference<T> typeReference) {
    try {
      return INSTANCE.readValue(json, typeReference);
    } catch (Exception e) {
      throw new RuntimeException("JSON反序列化失败", e);
    }
  }

  public static JsonNode toJsonNode(String jsonString) throws Exception {
    // 将JSON字符串解析为JsonNode对象
    return INSTANCE.readTree(jsonString);
  }
}
