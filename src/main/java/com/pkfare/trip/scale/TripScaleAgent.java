package com.pkfare.trip.scale;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.inspiration.InspirationAgent;
import com.pkfare.trip.scale.config.GoogleConfig;
import io.reactivex.rxjava3.core.Flowable;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TripScaleAgent {

  private static String NAME = "Coordinator";
  private static String USER_ID = "0987654321";

  public static BaseAgent ROOT_AGENT = initAgent();

  public static BaseAgent initAgent() {
    Gemini geminiModel = Gemini.builder().modelName("gemini-2.5-pro").apiKey(GoogleConfig.GOOGLE_API_KEY).build();
    return LlmAgent.builder()
        .name(NAME)
        .model(geminiModel)
        .description("Agent to help user to plan a trip.")
        .instruction(RootPrompt.INTRO)
        .subAgents(InspirationAgent.instance())
        .build();
  }

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);
    Session session =
        runner
            .sessionService()
            .createSession(NAME, USER_ID)
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

}
