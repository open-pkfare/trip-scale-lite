package com.pkfare.trip.scale.agent.inspiration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
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
import com.pkfare.trip.scale.assistance.PersonalPreferenceService;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import io.reactivex.rxjava3.core.Flowable;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class InspirationAgent extends BaseAgent {

  private static String NAME = "trip_inspiration_agent";

  private static BaseAgent INSTANCE;

  private static BaseAgent INSPIRATION_AGENT;

  protected InspirationAgent() {
    super("p_inspiration_agent", "Agent to help user to inspire trip demand into trip routes.",
        Lists.newArrayList(INSPIRATION_AGENT),
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE){
      INSPIRATION_AGENT = LlmAgent.builder()
          .name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to help user to inspire trip demand into trip routes.")
          .instruction(InspirationPrompt.TRIP_ROUTES_INSPIRATION)
          .tools(
              FunctionTool.create(PersonalPreferenceService.class, "preferences"))
          .build();
      INSTANCE = new InspirationAgent();
    }

    return INSTANCE;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    log.info("current agent: inspiration");
    invocationContext.branch("inspirator");
    return INSPIRATION_AGENT.runAsync(invocationContext);
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());

    TripDemand tripDemand = new TripDemand();
    tripDemand.setBudgets("15000CNY");
    tripDemand.setOrigin("shenzhen");
    tripDemand.setPassenger_number(1);
    tripDemand.setDays(14);
    tripDemand.setMust_go_destinations(Lists.newArrayList("Italy, Venice","Italy, Florence"));

    ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
    states.put("user:userId", "123");
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
