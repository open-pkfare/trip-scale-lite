package com.pkfare.trip.scale.assistance;

import com.google.adk.tools.Annotations.Schema;
import com.pkfare.trip.scale.dto.DestinationSuggestion;
import java.util.List;
import java.util.Map;

public class TripRouteService {

  private PersonalPreferenceService personalPreferenceService = new PersonalPreferenceService();

  public Map<String,String> tripRouteSuggestion(@Schema(name = "destinations", description = "destinations where user is willing to visit.") List<String> destinations, String userId){
    personalPreferenceService.preferences(userId);
    return null;
  }

}
