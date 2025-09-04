package com.pkfare.trip.scale.agent.optimizing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.Instruction.Provider;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.model.dto.TripDayInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class DailyChoseAgent extends BaseAgent {

  private static final String NAME = "trip_daily_chose_agent";

  private static BaseAgent INSTANCE;

  public DailyChoseAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public DailyChoseAgent() {
    super(NAME, "Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.",
        null,
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE) {
      Instruction instruction = new Provider(rc -> {
        TripRoutePlanResult result = (TripRoutePlanResult) rc.state().get("plan_result");
        // List<TripDayInfo> tripDayInfos = mockDailyPlans();
        List<TripDayInfo> tripDayInfos = new ArrayList<>();
        for (int i = 0; i < result.getDailyPlans().size(); i++) {
          tripDayInfos.add(new TripDayInfo(result.getDailyPlans().get(i), i + 1));
        }
        String prompt = StringUtils.replace(DailyChosePrompt.PROMPT, "{{trip_day_infos}}",
            JsonUtil.toJson(tripDayInfos));
        return Single.just(prompt);
      });
      INSTANCE = LlmAgent.builder()
          .name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.")
          .instruction(instruction)
          .build();
    }
    return INSTANCE;
  }

  private static List<TripDayInfo> mockDailyPlans() {
    List<TripDayInfo> tripDayInfos = new ArrayList<>();
    tripDayInfos.add(new TripDayInfo(LocalDate.of(2025, 10, 1), "IT", "Rome", 1));
    tripDayInfos.add(new TripDayInfo(LocalDate.of(2025, 10, 2), "IT", "Ostia", 2));
    tripDayInfos.add(new TripDayInfo(LocalDate.of(2025, 10, 3), "IT", "Paris", 3));
    return tripDayInfos;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    return Flowable.empty();
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return Flowable.empty();
  }


  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());
    Session session =
        runner
            .sessionService()
            .createSession(NAME, "test_daily_chose")
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("test_daily_chose", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }
}
