package com.pkfare.trip.scale.function;

import com.google.adk.agents.BaseAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;

public class AppRunner extends Runner {


  public AppRunner(BaseAgent agent, String appName,
      BaseSessionService sessionService) {
    super(agent, appName, new InMemoryArtifactService(), sessionService);
  }
}
