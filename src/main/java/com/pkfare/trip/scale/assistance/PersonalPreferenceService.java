package com.pkfare.trip.scale.assistance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.tools.Annotations.Schema;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.pkfare.trip.scale.config.MockHistoryConfig;
import com.pkfare.trip.scale.config.MockPreferenceConfig;
import com.pkfare.trip.scale.dto.Preferences;
import java.util.Map;

public class PersonalPreferenceService {

  public static Map<String, String> recentFocusAndHistoricalTrip(@Schema(name = "userId", description = "get user's historical trips and recent focus from social media by specific user id") String userId){
    Map<String, String> resp = Maps.newHashMap();
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      resp.put("historical_trips",mapper.writeValueAsString(MockHistoryConfig.historicalTrips()));
      resp.put("recent_focus",mapper.writeValueAsString(MockHistoryConfig.recentFocus()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return resp;
  }

  public static Map<String, String> preferences(@Schema(name = "userId", description = "get user's preferences by specific user id") String userId){
    Map<String, String> resp = Maps.newHashMap();

    Preferences preferences = MockPreferenceConfig.getPreferences();
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      resp.put("hates",mapper.writeValueAsString(preferences.getHates()));
      resp.put("likes",mapper.writeValueAsString(preferences.getLikes()));
      resp.put("preferred",new Gson().toJson(preferences.getPrefer()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return resp;
  }

}
