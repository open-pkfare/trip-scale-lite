package com.pkfare.trip.scale.agent.booking;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.agent.inspiration.DemandPrompt;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.DailySchedule;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BookingAgent extends BaseAgent {

  private static String NAME = "booking_agent";

  private static BookingAgent INSTANCE;

  private static InMemoryRunner runner;

  public BookingAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public BookingAgent() {
    super(NAME, "Agent to help user book and generate roadbook.",
        null,
        null,
        null);

    LlmAgent SUMMARY_AGENT = LlmAgent.builder().name(NAME)
        .model(GoogleConfig.GEMINI_2_5_FLASH)
        .description("Agent to help user to summarize daily trip plan info.")
        .instruction(BookingPrompt.SUMMARY_PROMPT).build();
    runner = new InMemoryRunner(SUMMARY_AGENT);
  }

  public static synchronized BookingAgent instance() {
    if (null == INSTANCE) {
      INSTANCE = new BookingAgent();
    }
    return INSTANCE;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    TripRoutePlanResult tripRoutePlanResult = (TripRoutePlanResult) invocationContext.session().state().get("plan_result");
    Map<String, String> summaries = summarize(invocationContext, tripRoutePlanResult);
    JSON json = generateRoadBook(tripRoutePlanResult, summaries);
    Event event = Event.builder().author("agent")
        .content(Content.builder().role("planner").parts(Lists.newArrayList(Part.fromText(json.toJSONString()))).build())
        .build();
    return Flowable.just(event);
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

  private Map<String, String> summarize(InvocationContext invocationContext, TripRoutePlanResult tripRoutePlanResult) {
    Map<String, String> all = Maps.newConcurrentMap();

    List<CompletableFuture<Void>> futures = tripRoutePlanResult.getDailyPlans().stream()
        .map(dailyPlan -> CompletableFuture.runAsync(() -> {
          Map<String, String> map = dailyPlan.getActivities().stream()
              .collect(Collectors.toMap(ActivityInfo::getActivityId, ai -> ai.getName() + " " + ai.getDescription()));
          Content content = Content.fromParts(Part.fromText("here are activities: \n" + JSON.toJSONString(map)));
          runner.runAsync(invocationContext.userId(), UUID.randomUUID().toString(), content)
              .blockingForEach(event -> {
                String text = event.content().get().text();
                Map<String, String> activitySummary = JSON.parseObject(text, Map.class);
                all.putAll(activitySummary);
              });
        }))
        .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return all;
  }

  private JSON generateRoadBook(TripRoutePlanResult tripRoutePlanResult, Map<String, String> summaries) {
    JSONArray all = new JSONArray();
    List<DailyRoutePlan> dailyPlans = tripRoutePlanResult.getDailyPlans();
    for (DailyRoutePlan dailyPlan : dailyPlans) {
      JSONArray daily = new JSONArray();
      List<ActivityInfo> activities = dailyPlan.getActivities();
      activities.forEach(activityInfo -> {
        JSONObject object = new JSONObject();
        object.put("name", activityInfo.getName());
        object.put("summary", summaries.get(activityInfo.getActivityId()));
        object.put("type", "act");
        daily.add(object);
      });

      all.add(daily);
    }
    return all;
  }

}
