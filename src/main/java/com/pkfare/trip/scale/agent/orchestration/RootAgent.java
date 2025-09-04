package com.pkfare.trip.scale.agent.orchestration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.web.config.DevConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pkfare.trip.scale.agent.booking.BookingAgent;
import com.pkfare.trip.scale.agent.inspiration.DemandAgent;
import com.pkfare.trip.scale.agent.inspiration.InspirationAgent;
import com.pkfare.trip.scale.agent.optimizing.DailyChoseAgent;
import com.pkfare.trip.scale.agent.optimizing.DailyOptimizingAgent;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentMap;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RootAgent extends BaseAgent {

  private static String NAME = "Coordinator";

  public static BaseAgent LLM_AGENT = instance();

  public static RootAgent ROOT_AGENT;
  @Setter
  private static DevConfig devConfig;

  public RootAgent() {
    super(NAME, "Agent to coordinate different agents to work together with different steps to finish a trip planning.",
        Lists.newArrayList(DemandAgent.instance(), InspirationAgent.instance(), PlanningAgent.instance(), DailyChoseAgent.instance(),
            DailyOptimizingAgent.instance(),
            BookingAgent.instance()),
        null,
        null);
  }

  public RootAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public static synchronized BaseAgent instance() {
    if (null == ROOT_AGENT){
      Instruction instruction = new Instruction.Provider(rc-> {
        String current_stage = (String) rc.state().get("current_stage");
        String prompt = StringUtils.replace(RootPrompt.INTRO,"{{current_stage}}", current_stage);
        return Single.just(prompt);
      });
      LLM_AGENT = LlmAgent.builder()
          .name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to coordinate different agents to work together with different steps to finish a trip planning.")
          .instruction(instruction)
          .subAgents(DemandAgent.instance(), InspirationAgent.instance(), PlanningAgent.instance(), DailyChoseAgent.instance(), DailyOptimizingAgent.instance(),
              BookingAgent.instance())
          .build();
      ROOT_AGENT = new RootAgent();
    }
    return ROOT_AGENT;
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
          devConfig.appendEvent(session, systemEvent);
    }
  }


  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    Session session = invocationContext.session();
    initSession(session);
    return devConfig.runner().runAsync(invocationContext.userId(), invocationContext.session().id(), invocationContext.userContent().get());
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }
}
