package com.pkfare.trip.scale.agent.introducing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntroducingAgent extends LlmAgent {

  private static String NAME = "trip_introduce_agent";

  private static BaseAgent INSTANCE;

  public IntroducingAgent(Builder builder) {
    super(builder);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {

    return null;
  }

}
