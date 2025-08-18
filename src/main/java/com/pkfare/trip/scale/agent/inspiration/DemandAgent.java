package com.pkfare.trip.scale.agent.inspiration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.pkfare.trip.scale.assistance.DestinationSuggestionService;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripCriteria;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DemandAgent {

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
              TripCriteria tripCriteria = new Gson().fromJson(text, TripCriteria.class);
              if (Objects.nonNull(tripCriteria)){
                aac.state().put("trip_demand", text);
              }
            }catch (Throwable e){
            }
          }
          return Maybe.fromOptional(aac.userContent());
        })
        .build();
  }

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());
    Session session =
        runner
            .sessionService()
            .createSession(NAME, "test_inspiration")
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("test_inspiration", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

}
