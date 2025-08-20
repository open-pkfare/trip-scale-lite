package com.pkfare.trip.scale.agent.inspiration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.pkfare.trip.scale.assistance.PersonalPreferenceService;
import com.pkfare.trip.scale.config.GoogleConfig;

public class SuggestionAgent {

  private static String NAME = "trip_suggestion_agent";

  private static BaseAgent INSTANCE;

  public static BaseAgent instance() {
    if (null == INSTANCE){
      INSTANCE = LlmAgent.builder()
          .name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to suggest destinations based on user preferences.")
          .instruction(SuggestionPrompt.SUGGESTION_PROMPT)
          .tools(
              FunctionTool.create(PersonalPreferenceService.class, "recentFocusAndHistoricalTrip"),
              FunctionTool.create(PersonalPreferenceService.class, "preferences"))
          .build();
    }
    return INSTANCE;
  }

}
