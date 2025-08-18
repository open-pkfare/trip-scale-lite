package com.pkfare.trip.scale.agent.orchestration;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.pkfare.trip.scale.agent.inspiration.DemandAgent;
import com.pkfare.trip.scale.agent.inspiration.InspirationAgent;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RootAgent {

  private static String NAME = "Coordinator";

  public static BaseAgent ROOT_AGENT = initAgent();

  public static InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

  private static final Map<String, Session> SESSION_MAP = Maps.newConcurrentMap();

  public static BaseAgent initAgent() {
    return LlmAgent.builder()
        .name(NAME)
        .model(GoogleConfig.GEMINI_2_5_FLASH)
        .description("Agent to coordinate different agents to work together with different steps to finish a trip planning.")
        .instruction(RootPrompt.INTRO)
        .subAgents(DemandAgent.instance(), InspirationAgent.instance())
        .build();
  }

  public List<RespConversation> chat(Conversation conversation) {
    Session session = initSession(conversation.getConversationId(), conversation.getUserId());

    Session updatedSession = runner.sessionService().getSession(NAME, conversation.getUserId(), session.id(), Optional.empty()).blockingGet();
    log.info("current session {}", updatedSession.state().entrySet());

    Content userMsg = Content.fromParts(Part.fromText(conversation.getContent()));

    Flowable<Event> events = runner.runAsync(conversation.getUserId(), session.id(), userMsg);
    StringBuilder stringBuilder = new StringBuilder();
    events.filter(UserEventFilter.instance()).blockingForEach(event -> {
      log.info("event {}", event.toString());
      if(event.content().isPresent()){
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
   * @param conversationId
   * @param userId
   * @return
   */
  private static Session initSession(String conversationId, String userId) {

    if (!SESSION_MAP.containsKey(conversationId)){
      log.info("start init a new session for conversation {}", conversationId);
      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("stage", "demand");
      states.put("userId", userId);

      EventActions actionsWithUpdate = EventActions.builder().stateDelta(states).build();
      long currentTimeMillis = Instant.now().toEpochMilli(); // Use milliseconds for Java Event

      Session session = runner.sessionService()
          .createSession(NAME, userId, states, conversationId)
          .blockingGet();
      SESSION_MAP.put(conversationId, session);

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
    return SESSION_MAP.get(conversationId);
  }


  public static void main(String[] args) {
    Session session = initSession("1234567890","123");

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
