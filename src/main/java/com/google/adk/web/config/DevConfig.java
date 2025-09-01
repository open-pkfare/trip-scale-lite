package com.google.adk.web.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.sessions.BaseSessionService;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.function.AppRunner;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevConfig {

  @Autowired
  private Map<String, BaseAgent> loadedAgentRegistry;
  @Autowired
  private BaseSessionService sessionService;

  @PostConstruct
  public void init(){
    AnotherRootAgent anotherRootAgent = AnotherRootAgent.instance();
    loadedAgentRegistry.put(anotherRootAgent.name(), anotherRootAgent);
    anotherRootAgent.setSessionService(sessionService);
  }

  @Bean
  public AppRunner runner(){
    return new AppRunner(AnotherRootAgent.instance(), "Coordinator", sessionService);
  }

}
