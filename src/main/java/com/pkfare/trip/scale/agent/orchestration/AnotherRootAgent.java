package com.pkfare.trip.scale.agent.orchestration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pkfare.trip.scale.agent.inspiration.DemandAgent;
import com.pkfare.trip.scale.agent.inspiration.InspirationAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnotherRootAgent extends BaseAgent {

  private static String NAME = "Coordinator";

  public static BaseAgent ROOT_AGENT = initAgent();
  public static InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

  public AnotherRootAgent() {
    super(NAME, "Agent to coordinate different agents to work together with different steps to finish a trip planning.",
        Lists.newArrayList(DemandAgent.instance(), InspirationAgent.instance()),
        null,
        null);
  }

  public AnotherRootAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public static BaseAgent initAgent() {
    return new AnotherRootAgent(
        NAME,
        "Agent to coordinate different agents to work together with different steps to finish a trip planning.",
        Lists.newArrayList(DemandAgent.instance(), InspirationAgent.instance()),
        null,
        null);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    String currentStage = (String) invocationContext.session().state().get("current_stage");
    Flowable<Event> eventFlowable = null;
    switch (currentStage) {
      case "demand":
        eventFlowable = invocationContext.agent().findAgent("trip_demand_agent").runAsync(invocationContext);
        break;
      case "inspiration":
        eventFlowable = invocationContext.agent().findAgent("trip_inspiration_agent").runAsync(invocationContext);
        break;

    }

    return eventFlowable.doAfterNext(event -> stageTransition(event, invocationContext));
  }

  public void stageTransition(Event event, InvocationContext invocationContext) {
    if (event.content().isPresent()) {
      Content content = event.content().get();
      String text = content.text();
      String role = content.role().get();
      if (StringUtils.isNotEmpty(text)) {
        Session session = invocationContext.session();
        String currentStage = (String) session.state().get("current_stage");
        ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
        try {
          text = text.replace("```json","").replace("```","");
          JsonElement jsonElement = JsonParser.parseString(text);
          switch (currentStage) {
            case "demand":
              TripDemand tripDemand = new Gson().fromJson(jsonElement, TripDemand.class);
              states.put("current_stage", "inspiration");
              states.put("trip_demand", tripDemand);
              List<Part> parts = content.parts().get();
              Part part = parts.getFirst().toBuilder().text(tripDemand.getBrief()).build();
              parts.removeFirst();
              parts.add(part);
              break;
            case "inspiration":
              List<TripRoute> tripRoutes = new Gson().fromJson(jsonElement, new TypeToken<List<TripRoute>>() {
              }.getType());
              states.put("current_stage", "inspiration");
              states.put("trip_routes", tripRoutes);
              break;
            default:
          }
        } catch (Throwable e) {
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
            runner.sessionService().appendEvent(session, systemEvent).blockingGet();
      }
    }
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

  public List<RespConversation> chat(Conversation conversation) {
    Session session = initSession(conversation.getConversationId(), conversation.getUserId());

    Session updatedSession = runner.sessionService()
        .getSession(NAME, conversation.getUserId(), session.id(), Optional.empty()).blockingGet();
    log.info("current session {}", updatedSession.state().entrySet());

    Content userMsg = Content.fromParts(Part.fromText(conversation.getContent()));

    Flowable<Event> events = runner.runAsync(conversation.getUserId(), session.id(), userMsg);
    StringBuilder stringBuilder = new StringBuilder();
    events.filter(UserEventFilter.instance()).blockingForEach(event -> {
//      setDone(event, conversation.getUserId(), conversation.getConversationId());
      log.info("event {}", event);
      if (event.content().isPresent()) {
        Content content = event.content().get();
        stringBuilder.append(content.text());
      }
    });

    RespConversation respConversation = new RespConversation();
    respConversation.setType("string");
    respConversation.setContent(stringBuilder.toString());
    respConversation.setConversationId(conversation.getConversationId());

    List<RespConversation> respConversations = Lists.newArrayList();
    respConversations.add(respConversation);
    return respConversations;
  }

  /**
   * init session dialog
   *
   * @param conversationId
   * @param userId
   * @return
   */
  private static Session initSession(String conversationId, String userId) {
    Maybe<Session> sessionMaybe = runner.sessionService().getSession(NAME, userId, conversationId, Optional.empty());
    Session session;
    if (null == (session = sessionMaybe.blockingGet())) {
      log.info("start init a new session for conversation {}", conversationId);
      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("current_stage", "demand");
      states.put("user:userId", userId);

      session = runner.sessionService()
          .createSession(NAME, userId, states, conversationId)
          .blockingGet();
    }
    return session;
  }


}
