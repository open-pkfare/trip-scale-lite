package com.pkfare.trip.scale.assistance;

import com.google.adk.tools.Annotations.Schema;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.pkfare.trip.scale.config.MockDestinationConfig;
import com.pkfare.trip.scale.dto.DestinationSuggestion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DestinationSuggestionService {


  private static int maxSuggestions = 3;

  public static Map<String, String> getDestinationSuggestions(
      @Schema(name = "userId", description = "suggest destinations for specific user") String userId) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("用户 ID 不能为空");
    }

    Map<String, String> resp = Maps.newHashMap();

    try {
      // 对于模拟实现，返回配置的目的地子集
      // 在真实实现中，这将基于用户偏好进行个性化
      List<DestinationSuggestion> suggestions = new ArrayList<>(MockDestinationConfig.getSuggestions());

      // 随机打乱以为不同请求提供多样性
      Collections.shuffle(suggestions);

      // 限制到最大建议数量
      int limit = Math.min(maxSuggestions, suggestions.size());
      List<DestinationSuggestion> result = suggestions.subList(0, limit);

      resp.put("status", "success");
      resp.put("suggestions", new Gson().toJson(result));
      return resp;

    } catch (Exception e) {
      resp.put("status", "fail");
      resp.put("message", "fail to fetch destination suggestion for user " + userId);
      return resp;
    }
  }

}
