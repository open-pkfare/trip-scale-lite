package com.google.adk.web.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.function.AppRunner;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DevConfig {

  @Autowired
  private Map<String, BaseAgent> loadedAgentRegistry;
  @Autowired
  private BaseSessionService sessionService;

  public static final String APP_NAME = "Coordinator";

  @PostConstruct
  public void init(){
    AnotherRootAgent anotherRootAgent = AnotherRootAgent.instance();
    loadedAgentRegistry.put(anotherRootAgent.name(), anotherRootAgent);
    anotherRootAgent.setDevConfig(this);
  }

  @Bean
  public AppRunner runner(){
    return new AppRunner(AnotherRootAgent.instance(), APP_NAME, sessionService);
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
    Maybe<Session> sessionMaybe = sessionService.getSession(APP_NAME, userId, conversationId, Optional.empty());
    Session session;
    if (null == (session = sessionMaybe.blockingGet())) {
      log.info("start init a new session for conversation {}", conversationId);
      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("current_stage", "demand");
      states.put("user:userId", userId);

      session = sessionService
          .createSession(APP_NAME, userId, states, conversationId)
          .blockingGet();
    }
    return session;
  }

  public Event appendEvent(Session session, Event event){
    return sessionService.appendEvent(session,event).blockingGet();
  }

}
