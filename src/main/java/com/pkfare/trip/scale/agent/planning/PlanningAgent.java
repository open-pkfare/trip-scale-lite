package com.pkfare.trip.scale.agent.planning;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Slf4j
public class PlanningAgent extends BaseAgent {

  private static String NAME = "trip_planning_agent";

  private static BaseAgent INSTANCE;
  
  // 静态引用ApplicationContext，用于获取Spring Bean
  private static ApplicationContext applicationContext;

  public PlanningAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public PlanningAgent() {
    super(NAME, "Agent to help user to plan a trip with specific trip routes.",
        null,
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE){
      INSTANCE = new PlanningAgent();
    }
    return INSTANCE;
  }

  public static void setApplicationContext(ApplicationContext context) {
    applicationContext = context;
  }


  /**
   * 获取GeneratePlanService实例
   */
  private GeneratePlanService getGeneratePlanService() {
    if (applicationContext == null) {
      throw new IllegalStateException("ApplicationContext not set. Please call setApplicationContext() first.");
    }
    return applicationContext.getBean(GeneratePlanService.class);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    log.info("Starting PlanningAgent runAsyncImpl");
    
    try {
      // 从会话状态中获取数据
      TripDemand tripDemand = (TripDemand)invocationContext.session().state().get("trip_demand");
      List<TripRoute> tripRoutes = (List<TripRoute>)invocationContext.session().state().get("trip_route");

      /**
      if (tripDemand == null || tripRoutes == null) {
        log.error("TripDemand or TripRoutes is null in session state");
        return Flowable.error(new IllegalStateException("Missing required data in session"));
      }
      
      log.info("Retrieved trip demand: origin={}, days={}, passengers={}", 
          tripDemand.getOrigin(), tripDemand.getDays(), tripDemand.getPassenger_number());
      log.info("Retrieved {} trip routes", tripRoutes.size());

      // 通过tripDemand和tripRoutes构建GeneratePlanParam
      GeneratePlanParam param = buildGeneratePlanParam(tripDemand, tripRoutes);
      **/
      GeneratePlanParam param = mockGeneratePlanParam();
      // 调用GeneratePlanService.generatePlan接口
      GeneratePlanService generatePlanService = getGeneratePlanService();
      long start = System.currentTimeMillis();
      TripRoutePlanResult planResult = generatePlanService.generatePlan(param);
      log.info("*******************************************************Time taken to generate plan: {} ms", System.currentTimeMillis() - start   );
      
      log.info("Generated trip plan with status: {}", planResult.getStatus());

      // 拆成两个event：TripRoutePlanResult.summary 摘要是单独的一个Event，TripRoutePlanResult是一个单独的event
      return createPlanEvents(planResult, invocationContext);
      
    } catch (Exception e) {
      log.error("Error in PlanningAgent runAsyncImpl", e);
      return Flowable.error(e);
    }
  }

  private GeneratePlanParam mockGeneratePlanParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("FLR");
    param.setLocation_code("IT");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-07");
    param.setTrip_days(7);
    param.setAdult_number(1);
    param.setChild_number(1);
    param.setRoom_quantity(1);
    param.setBudgets("50000");
    param.setCurrency("CNY");
    param.setTrip_routes(buildOneWayTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildOneWayTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(2, "Rome", "IT", "FCO"));
    tripRouteParams.add(buildRouteTrip(2, "Ostia", "IT", "OST"));
    tripRouteParams.add(buildRouteTrip(3, "Paris", "FR", "ORY"));
    return tripRouteParams;
  }

  private TripRouteParam buildRouteTrip(int days, String destinationCity, String countryCode, String locationCode) {
    TripRouteParam param = new TripRouteParam();
    param.setStay_days(days);
    param.setDestination_city(destinationCity);
    param.setLocation_code(locationCode);
    param.setCountry_code(countryCode);
    param.setReason_for_recommendation("recommendation");
    return param;
  }

  /**
   * 创建计划事件
   * 
   * @param planResult 计划结果
   * @param invocationContext 调用上下文
   * @return 事件流
   */
  private Flowable<Event> createPlanEvents(TripRoutePlanResult planResult, InvocationContext invocationContext) {
    List<Event> events = new ArrayList<>();
    long currentTimeMillis = Instant.now().toEpochMilli();
    
    // 第一个事件：摘要事件
    if (planResult.getSummary() != null && !planResult.getSummary().isEmpty()) {
      Content summaryContent = Content.fromParts(Part.fromText(planResult.getSummary()));
      Event summaryEvent = Event.builder()
          .invocationId(invocationContext.invocationId())
          .author("agent")
          .content(summaryContent)
          .timestamp(currentTimeMillis)
          .build();
      events.add(summaryEvent);
      
      log.info("Created summary event with content length: {}", planResult.getSummary().length());
    }
    
    // 第二个事件：完整计划结果事件
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      String planResultJson = mapper.writeValueAsString(planResult);
      Content planContent = Content.builder().role("planner").parts(Lists.newArrayList(Part.fromText(planResultJson))).build();
      Event planEvent = Event.builder()
          .invocationId(invocationContext.invocationId())
          .author("agent")
          .content(planContent)
          .timestamp(currentTimeMillis + 1) // 稍微延后一毫秒确保顺序
          .build();
      events.add(planEvent);
      
      log.info("Created plan result event with {} daily plans", 
          planResult.getDailyPlans() != null ? planResult.getDailyPlans().size() : 0);
      
    } catch (Exception e) {
      log.error("Failed to serialize plan result to JSON", e);
      // 如果序列化失败，创建一个错误事件
      Content errorContent = Content.fromParts(Part.fromText("Error: Failed to generate detailed plan"));
      Event errorEvent = Event.builder()
          .invocationId(invocationContext.invocationId())
          .author("agent")
          .content(errorContent)
          .timestamp(currentTimeMillis + 1)
          .build();
      events.add(errorEvent);
    }
    
    return Flowable.fromIterable(events);
  }

  private GeneratePlanParam buildGeneratePlanParam(TripDemand tripDemand, List<TripRoute> tripRoutes) {
    if (tripDemand == null || tripRoutes == null || tripRoutes.isEmpty()) {
      throw new IllegalArgumentException("TripDemand and TripRoutes cannot be null or empty");
    }
    
    GeneratePlanParam param = new GeneratePlanParam();
    
    // 基本信息
    param.setOrigin(tripDemand.getOrigin());
    param.setLocation_code(tripDemand.getOrigin_country_code()); // 默认设置为美国，可根据实际需求调整
    param.setTrip_days(tripDemand.getDays());
    param.setBudgets(tripDemand.getBudgets());
    param.setCurrency(tripDemand.getCurrency()); // 默认美元
    
    // 乘客信息
    param.setAdult_number(Math.max(1, tripDemand.getPassenger_number())); // 至少1个成人
    param.setChild_number(0); // 默认无儿童
    param.setRoom_quantity(1); // 默认1个房间
    param.setStart_period(tripDemand.getStart_period());
    param.setEnd_period(tripDemand.getEnd_period());
    
    // 转换路线信息
    List<TripRouteParam> routeParams = new ArrayList<>();
    for (TripRoute tripRoute : tripRoutes) {
      TripRouteParam routeParam = new TripRouteParam();
      routeParam.setDestination_city(tripRoute.getDestination_city());
      routeParam.setStay_days(tripRoute.getStay_days());
      routeParam.setReason_for_recommendation(tripRoute.getReason_for_recommendation());
      routeParam.setCountry_code(tripRoute.getCountry_code()); // 默认美国
      routeParam.setLocation_code(tripRoute.getLocation_code()); // 生成位置代码
      routeParams.add(routeParam);
    }
    param.setTrip_routes(routeParams);
    
    return param;
  }


  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

}
