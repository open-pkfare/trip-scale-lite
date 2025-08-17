package com.pkfare.trip.scale.config;

import com.google.common.collect.Lists;
import com.pkfare.trip.scale.dto.Preferences;

public class MockPreferenceConfig {

  public static Preferences getPreferences(){
    return new Preferences(
        Lists.newArrayList("ancient building","history","art","local food","city walk","religious story"),
        Lists.newArrayList("modern building","crowded place","fast food","noisy environment"),
        Lists.newArrayList("lone ranger","adventure","price-sensitive")
    );
  }

}
