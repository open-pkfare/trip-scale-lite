package com.pkfare.trip.scale.gson.deserializer;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
public class DateDeserializer implements JsonDeserializer<Date> {

  // 支持多种日期格式（可选，根据项目需求调整）
  private static final String[] DATE_FORMATS = {
      "yyyy-MM-dd",
      "yyyy-MM-dd HH:mm:ss",
      "yyyy/MM/dd",
      "yyyy/MM/dd HH:mm:ss"
  };

  @Override
  public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {

    if (json.isJsonNull()) {
      return null; // 处理 null 值
    }

    String dateStr = json.getAsString().trim();
    if (dateStr.isEmpty()) {
      return null; // 处理空字符串
    }

    // 尝试多种格式解析（灵活适配不同场景）
    for (String format : DATE_FORMATS) {
      try {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setLenient(false); // 严格模式（避免宽松解析，如 "2025-02-30" 被解析为 2025-03-01）
        return sdf.parse(dateStr);
      } catch (ParseException e) {
        // 忽略当前格式，尝试下一个
      }
    }

    // 所有格式都失败时抛出异常
    throw new JsonParseException("无法解析日期: " + dateStr + "，支持的格式: " + String.join(", ", DATE_FORMATS));
  }

}
