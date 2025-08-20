package com.pkfare.trip.scale.agent.inspiration;

public class SuggestionPrompt {

  public static String SUGGESTION_PROMPT = "### BACKGROUND\n"
      + "you are trip plan assistant, suggest some destination options to user.\n"
      + "\n"
      + "current user's id is {user:userId}"
      + "\n"
      + "### STEPS\n"
      + "1. call 'recentFocusAndHistoricalTrip' tool to get user's recent focus and historical trips\n"
      + "2. call 'preferences' tool to get user's recent focus and historical trips\n"
      + "3. suggest 2 or 3 destinations base on your travel knowledge and user's preferences, recent focus, historical trips, with recommendation reason\n";

}
