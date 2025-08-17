package com.pkfare.trip.scale.assistance;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.pkfare.trip.scale.config.MockPreferenceConfig;
import com.pkfare.trip.scale.dto.Preferences;
import java.util.Map;

public class PersonalPreferenceService {

  public Map<String, String> preferences(String userId){
    Map<String, String> resp = Maps.newHashMap();

    Preferences preferences = MockPreferenceConfig.getPreferences();

    resp.put("hates",new Gson().toJson(preferences.getHates()));
    resp.put("likes",new Gson().toJson(preferences.getLikes()));
    resp.put("preferred",new Gson().toJson(preferences.getPrefer()));

    return resp;
  }

}
