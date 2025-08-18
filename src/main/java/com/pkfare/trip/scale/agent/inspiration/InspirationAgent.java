package com.pkfare.trip.scale.agent.inspiration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pkfare.trip.scale.assistance.PersonalPreferenceService;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentMap;

public class InspirationAgent {

  private static String NAME = "trip_inspiration_agent";

  public static BaseAgent instance() {
    return LlmAgent.builder()
        .name(NAME)
        .model(GoogleConfig.GEMINI_2_5_PRO)
        .description("Agent to help user to inspire trip demand into trip routes.")
        .instruction(InspirationPrompt.TRIP_ROUTES_INSPIRATION)
        .tools(
            FunctionTool.create(PersonalPreferenceService.class, "preferences"))
        .afterAgentCallback(aac -> {
          //predict if done
          Optional<Content> contentOptional = aac.userContent();
          if (contentOptional.isPresent()){
            Content content = contentOptional.get();
            String text = content.text();
            try {
              List<TripRoute> tripRoutes = new Gson().fromJson(text, new TypeToken<List<TripRoute>>(){}.getType());
              if (null != tripRoutes){//当前agent结束
                aac.state().put("trip_routes", text);
                aac.state().put("stage","planning");
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

    TripDemand tripDemand = new TripDemand();
    tripDemand.setBudgets("15000CNY");
    tripDemand.setOrigin("shenzhen");
    tripDemand.setPassengerNumber(1);
    tripDemand.setDays(14);
    tripDemand.setMustGoDestinations(Lists.newArrayList("Italy, Venice","Italy, Florence"));

    ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
    states.put("userId", "123");
    Session session =
        runner
            .sessionService()
            .createSession(NAME, "123", states, "456")
            .blockingGet();

    Content init = Content.fromParts(Part.fromText("here's my demand: " + new Gson().toJson(tripDemand)));
    Flowable<Event> initEvents = runner.runAsync("123", session.id(), init);
    System.out.print("\nTripScale > ");
    initEvents.blockingForEach(event -> System.out.println(event.stringifyContent()));

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("123", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

}
