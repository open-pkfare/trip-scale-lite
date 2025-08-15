package com.pkfare.trip.scale.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.assistance.DestinationSuggestionService;
import com.pkfare.trip.scale.config.GoogleConfig;
import io.reactivex.rxjava3.core.Flowable;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class InspirationAgent {

  private static String NAME = "trip_inspiration_agent";

  public static BaseAgent instance() {
    Gemini geminiModel = new Gemini("gemini-2.5-pro", GoogleConfig.GOOGLE_API_KEY);
    return LlmAgent.builder()
        .name(NAME)
        .model(geminiModel)
        .description("Agent to help user to inspire and collect trip demand info.")
        .instruction(InspirationPrompt.DEMAND_AND_PREFERENCE_INSPIRATION)
        .tools(
            FunctionTool.create(DestinationSuggestionService.class, "getDestinationSuggestions"))
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
