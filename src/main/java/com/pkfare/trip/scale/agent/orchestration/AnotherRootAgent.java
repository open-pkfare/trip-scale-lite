package com.pkfare.trip.scale.agent.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pkfare.trip.scale.agent.inspiration.DemandAgent;
import com.pkfare.trip.scale.agent.inspiration.InspirationAgent;
import com.pkfare.trip.scale.agent.optimizing.OptimizingAgent;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
public class AnotherRootAgent extends BaseAgent {

  private static String NAME = "Coordinator";

  private static AnotherRootAgent ROOT_AGENT;

  @Setter
  private BaseSessionService sessionService;

  public AnotherRootAgent() {
    super(NAME, "Agent to coordinate different agents to work together with different steps to finish a trip planning.",
        Lists.newArrayList(DemandAgent.instance(), InspirationAgent.instance(), PlanningAgent.instance(), OptimizingAgent.instance()),
        null,
        null);
  }

  public AnotherRootAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public static synchronized AnotherRootAgent instance() {
    if (null == ROOT_AGENT){
      ROOT_AGENT = new AnotherRootAgent();
    }
    return ROOT_AGENT;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    Session session = invocationContext.session();
    initSession(session);
    getSession(session.id(), session.userId());
    String currentStage = (String) session.state().get("current_stage");
    Flowable<Event> eventFlowable = null;
    switch (currentStage) {
      case "demand":
        eventFlowable = invocationContext.agent().findAgent("trip_demand_agent").runAsync(invocationContext);
        break;
      case "inspiration":
        eventFlowable = invocationContext.agent().findAgent("trip_inspiration_agent").runAsync(invocationContext);
        break;
      case "planning":
        eventFlowable = invocationContext.agent().findAgent("trip_planning_agent").runAsync(invocationContext);
        break;
      case "optimizing":
        eventFlowable = invocationContext.agent().findAgent("trip_optimizing_agent").runAsync(invocationContext);
        break;
    }
    assert eventFlowable != null;
    return eventFlowable.doOnNext(event-> log.info("inner event {}", event.id()))
//        .mergeWith(Single.fromSupplier(()-> Event.builder().author("system").content(Content.fromParts(Part.fromText("hi night."))).build()))
        .doOnNext(event -> stageTransition(event, invocationContext));
  }

  public void stageTransition(Event event, InvocationContext invocationContext) {
    if (event.content().isPresent()) {
      Content content = event.content().get();
      String text = content.text();
      if (StringUtils.isNotEmpty(text)) {
        Session session = invocationContext.session();
        String currentStage = (String) session.state().get("current_stage");
        ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
        String pref = null;
        try {
          if (text.contains("------")) {
            String[] tt = text.split("------");
            pref = tt[0];
            text = tt[1];
          }
          text = text.replace("```json","").replace("```","");

          ObjectMapper mapper = new ObjectMapper();
          mapper.registerModule(new JavaTimeModule());
          mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

          //JsonElement jsonElement = JsonParser.parseString(text);
          List<Part> parts = content.parts().get();
          Part part;
          switch (currentStage) {
            case "demand":
              TripDemand tripDemand = mapper.readValue(text, TripDemand.class);
              states.put("current_stage", "inspiration");
              states.put("trip_demand", tripDemand);
              part = Part.builder().text(tripDemand.getBrief()).build();
              parts.removeFirst();
              parts.add(part);
              break;
            case "inspiration":
              List<TripRoute> tripRoutes = mapper.readValue(text, new TypeReference<List<TripRoute>>() {});
              states.put("current_stage", "planning");
              states.put("trip_route", tripRoutes);
              part = Part.builder().text(Optional.ofNullable(pref).orElse("Let's will start planning details for it!")).build();
              parts.removeFirst();
              parts.add(part);
              break;
            case "planning":
              if (content.role().isPresent() && "planner".equals(content.role().get())){
//                TripRoutePlanResult tripRoutePlanResult = JSON.parseObject(text,TripRoutePlanResult.class);
                //TripRoutePlanResult tripRoutePlanResult = new Gson().fromJson(text, TripRoutePlanResult.class);
                TripRoutePlanResult tripRoutePlanResult = mapper.readValue(text, TripRoutePlanResult.class);
                states.put("current_stage", "optimizing");
                states.put("plan_result", tripRoutePlanResult);
//                Part part1 = Part.builder().text(tripRoutePlanResult.getSummary()).build();
                part = Part.builder().text(text).build();
                parts.removeFirst();
//                parts.add(part1);
                parts.add(part);
              }
            case "optimizing":
              TripRoutePlanResult tripRoutePlanResult = mapper.readValue(text, TripRoutePlanResult.class);
              states.put("current_stage", "booking");
              states.put("optimize_result", tripRoutePlanResult);
              part = Part.builder().text(text).build();
              parts.removeFirst();
              parts.add(part);
            default:
          }
        } catch (Throwable e) {
//          log.info("error {}", ExceptionUtils.getStackTrace(e));
          log.info("parse error text : {}", text);
          return;
        }

        EventActions actionsWithUpdate = EventActions.builder().stateDelta(states).build();
        long currentTimeMillis = Instant.now().toEpochMilli(); // Use milliseconds for Java Event
        Event systemEvent =
            Event.builder()
                .invocationId("init")
                .author("system") // Or 'agent', 'tool' etc.
                .actions(actionsWithUpdate)
                .timestamp(currentTimeMillis)
                // content might be None or represent the action taken
                .build();
        Event updatedSession =
            sessionService.appendEvent(session, systemEvent).blockingGet();
      }
    }
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

  /**
   * init session dialog
   *
   * @param conversationId
   * @param userId
   * @return
   */
  @CanIgnoreReturnValue
  public Session getSession(String conversationId, String userId) {
    Maybe<Session> sessionMaybe = sessionService.getSession(NAME, userId, conversationId, Optional.empty());
    Session session;
    if (null == (session = sessionMaybe.blockingGet())) {
      log.info("start init a new session for conversation {}", conversationId);
      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("current_stage", "demand");
      states.put("user:userId", userId);

      session = sessionService
          .createSession(NAME, userId, states, conversationId)
          .blockingGet();
    }
    return session;
  }

  private void initSession(Session session){
    if (!session.state().containsKey("current_stage")){
      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("current_stage", "demand");
      states.put("user:userId", session.userId());
      EventActions actionsWithUpdate = EventActions.builder().stateDelta(states).build();
      long currentTimeMillis = Instant.now().toEpochMilli(); // Use milliseconds for Java Event
      Event systemEvent =
          Event.builder()
              .invocationId("init")
              .author("system") // Or 'agent', 'tool' etc.
              .actions(actionsWithUpdate)
              .timestamp(currentTimeMillis)
              // content might be None or represent the action taken
              .build();
      Event updatedSession =
          sessionService.appendEvent(session, systemEvent).blockingGet();
    }
  }

}
