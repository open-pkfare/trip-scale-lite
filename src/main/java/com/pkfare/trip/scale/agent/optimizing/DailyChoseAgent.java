package com.pkfare.trip.scale.agent.optimizing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.Instruction.Provider;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.web.config.DevConfig;
import com.google.common.collect.Lists;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyChoseAgent extends BaseAgent {

  private static final String CURRENT_AGENT = "trip_daily_chose_agent";
  private static final String BASE_AGENT = "trip_daily_chose_base_agent";
  public static final String CHOSE_DAY_PLAN = "chose_day_plan";

  private static BaseAgent INSTANCE;

  private static DailyChoseAgent dailyChoseAgent;

  @Autowired
  private DevConfig devConfig;

  public DailyChoseAgent() {
    super(CURRENT_AGENT, "Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.",
        Lists.newArrayList(INSTANCE, DailyOptimizingAgent.instance()),
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE) {
      Instruction instruction = new Provider(rc -> {
        TripRoutePlanResult result = (TripRoutePlanResult) rc.state().get("plan_result");
         List<TripDayInfo> tripDayInfos = mockDailyPlans();
//        List<TripDayInfo> tripDayInfos = new ArrayList<>();
//        for (int i = 0; i < result.getDailyPlans().size(); i++) {
//          tripDayInfos.add(new TripDayInfo(result.getDailyPlans().get(i), i + 1));
//        }
        String prompt = StringUtils.replace(DailyChosePrompt.PROMPT, "{{trip_day_infos}}",
            JsonUtil.toJson(tripDayInfos));
        return Single.just(prompt);
      });
      INSTANCE = LlmAgent.builder()
          .name(BASE_AGENT)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.")
          .instruction(instruction)
          .build();
      dailyChoseAgent = new DailyChoseAgent();
    }
    return dailyChoseAgent;
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
   return invocationContext.agent().findAgent(BASE_AGENT).runAsync(invocationContext).doOnNext(event -> {
      Content content = event.content().get();
      Optional<String> optional = parse(Objects.requireNonNull(content.text()));
      if (optional.isPresent()) {
        invocationContext.session().state().put(CHOSE_DAY_PLAN, optional.get());
//        devConfig.appendEvent(invocationContext.session(), event);
        invocationContext.agent().findAgent(DailyOptimizingAgent.CURRENT_AGENT).runAsync(invocationContext);
      }
    });
  }

  private Optional<String> parse(String text) {
    if (!text.contains("------")) {
      return Optional.empty();
    }
    String[] tt = text.split("------");
    text = tt[1];
    text = text.replace("```json", "").replace("```", "");
    return Optional.of(text);
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
            .createSession(CURRENT_AGENT, "test_daily_chose")
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
