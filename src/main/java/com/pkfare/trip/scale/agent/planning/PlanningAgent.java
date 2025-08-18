package com.pkfare.trip.scale.agent.planning;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.gson.Gson;
import com.pkfare.trip.scale.agent.inspiration.DemandPrompt;
import com.pkfare.trip.scale.assistance.DestinationSuggestionService;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Objects;
import java.util.Optional;

public class PlanningAgent {

  private static String NAME = "trip_demand_agent";

  public static BaseAgent instance() {
    return LlmAgent.builder()
        .name(NAME)
        .model(GoogleConfig.GEMINI_2_5_PRO)
        .description("Agent to help user to inspire and collect trip demand info.")
        .instruction(DemandPrompt.DEMAND_AND_PREFERENCE_INSPIRATION)
        .tools(
            FunctionTool.create(DestinationSuggestionService.class, "getDestinationSuggestions"))
        .afterAgentCallback(aac -> {
          //predict if done
          Optional<Content> contentOptional = aac.userContent();
          if (contentOptional.isPresent()){
            Content content = contentOptional.get();
            String text = content.text();
            try {
              TripDemand tripDemand = new Gson().fromJson(text, TripDemand.class);
              if (Objects.nonNull(tripDemand)){
                aac.state().put("trip_demand", text);
              }
            }catch (Throwable e){
            }
          }
          return Maybe.fromOptional(aac.userContent());
        })
        .build();
  }

}
