package com.pkfare.trip.scale.agent.planning;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.tools.FunctionTool;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.gson.Gson;
import com.pkfare.trip.scale.agent.inspiration.DemandAgent;
import com.pkfare.trip.scale.agent.inspiration.DemandPrompt;
import com.pkfare.trip.scale.agent.inspiration.InspirationAgent;
import com.pkfare.trip.scale.assistance.DestinationSuggestionService;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class PlanningAgent extends BaseAgent {

  private static String NAME = "trip_planning_agent";

  private static BaseAgent INSTANCE;

  public PlanningAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public PlanningAgent() {
    super(NAME, "Agent to help user to plan a trip with specific trip routes.",
        Lists.newArrayList(DemandAgent.instance(), InspirationAgent.instance()),
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE){
      INSTANCE = new PlanningAgent();
    }
    return INSTANCE;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    TripDemand tripDemand = (TripDemand)invocationContext.session().state().get("trip_demand");
    List<TripRoute> tripRoutes = (List<TripRoute>)invocationContext.session().state().get("trip_routes");

    //todo

    return null;
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }
}
