package com.pkfare.trip.scale.agent.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.web.config.DevConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.PlanResultCacheService;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.CollectionUtils;

@Slf4j
public class PlanningAgent extends BaseAgent {

  private static String NAME = "trip_planning_agent";

  private static PlanningAgent INSTANCE;

  // 静态引用ApplicationContext，用于获取Spring Bean
  private static ApplicationContext applicationContext;

  public static BaseAgent LLM_AGENT;

  @Setter
  private DevConfig devConfig;

  public PlanningAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public PlanningAgent() {
    super(NAME, "Agent to help user to plan a purchasable trip with specific trip routes.",
        null,
        null,
        null);
  }

  public static PlanningAgent instance() {
    if (null == INSTANCE) {

      LLM_AGENT = LlmAgent.builder()
          .name("extractor")
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to extract key data from dialog.")
          .instruction(PlanningPrompt.PLANNING_PROMPT)
          .generateContentConfig(GenerateContentConfig.builder().temperature(0.2f).build())
          .build();
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

  /**
   * 获取PlanResultCacheService实例
   */
  private PlanResultCacheService getPlanResultCacheService() {
    if (applicationContext == null) {
      throw new IllegalStateException("ApplicationContext not set. Please call setApplicationContext() first.");
    }
    return applicationContext.getBean(PlanResultCacheService.class);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    log.info("current agent: planning");
    try {
      // 从会话状态中获取数据
      TripDemand tripDemand = (TripDemand) invocationContext.session().state().get("trip_demand");
      List<TripRoute> tripRoutes = (List<TripRoute>) invocationContext.session().state().get("trip_route");
      AtomicReference<List<TripRoute>> tripRoutesAt = new AtomicReference<>();
      if (CollectionUtils.isEmpty(tripRoutes)) {
        log.info("no result in states. start extract from dialog.");
        invocationContext.branch("extract");
        LLM_AGENT.runAsync(invocationContext).blockingForEach(event -> {
          Optional<Content> optional = event.content();
          Content content = optional.get();
          String text = content.text();
          tripRoutesAt.set(extract(text));
        });
      }
      tripRoutes = tripRoutesAt.get();

      log.info("tripDemand:{},tripRoutes:{}", tripDemand, tripRoutes);
      GeneratePlanService generatePlanService = getGeneratePlanService();

      GeneratePlanParam param = null;
      if (tripRoutes == null) {
        //return Flowable.error(new IllegalStateException("Missing required tripRoutes in session"));
        param = mockSZXItalyGeneratePlanParam();;
      }else if (tripDemand ==null) {
        //return Flowable.error(new IllegalStateException("Missing required tripDemand in session"));
        param = mockDefaultGeneratePlanParam(tripRoutes);
      }else{
        // 1. 参数验证
        param = buildGeneratePlanParam(tripDemand,tripRoutes);
        boolean checkSuccess = generatePlanService.validateParams(param);
        if(!checkSuccess){
          param = mockDefaultGeneratePlanParam(tripRoutes);
        }else{
          // 通过tripDemand和tripRoutes构建GeneratePlanParam
          param = buildGeneratePlanParam(tripDemand, tripRoutes);
        }
      }
      param.setOrigin("SZX");
      param.setStart_period("2025-10-01");
      param.setEnd_period("2025-10-20");
      //GeneratePlanParam param = mockSZXGeneratePlanParam();
      log.info("GeneratePlanParam:{}", JsonUtil.toJson(param));

      // 调用GeneratePlanService.generatePlan接口
      long start = System.currentTimeMillis();
      TripRoutePlanResult planResult = generatePlanService.generatePlan(param);

      ConcurrentMap<String, Object> states = Maps.newConcurrentMap();
      states.put("current_stage", "optimizing");
      states.put("plan_result", planResult);
      states.put("trip_route", tripRoutes);
      devConfig.saveState(invocationContext.session(), states);
      log.info("*******************************************************Time taken to generate plan: {} ms",
          System.currentTimeMillis() - start);

      log.info("Generated trip plan with status: {}", planResult.getStatus());

      // 拆成两个event：TripRoutePlanResult.summary 摘要是单独的一个Event，TripRoutePlanResult是一个单独的event
      return createPlanEvents(planResult, invocationContext);

    } catch (Exception e) {
      log.error("Error in PlanningAgent runAsyncImpl", e);
      return Flowable.error(e);
    }
  }

  private GeneratePlanParam mockDefaultGeneratePlanParam(List<TripRoute> tripRoutes) {
    if(isItalyTrip(tripRoutes)){
      return mockSZXItalyGeneratePlanParam();
    }else if(isThailandChiangMaiTrip(tripRoutes)){
      return mockSZXThailandChiangMaiGeneratePlanParam();
    }else{
      return mockSZXThailandGeneratePlanParam();
    }
  }

  private boolean isThailandChiangMaiTrip(List<TripRoute> tripRoutes) {
    // 通过trip_routes中的destination_city判断，如果有Chiang Mai，返回true，否则false
    if (CollectionUtils.isEmpty(tripRoutes)) {
      return false;
    }
    
    return tripRoutes.stream()
        .anyMatch(route -> route != null && 
            "Chiang Mai".equalsIgnoreCase(route.getDestination_city()));
  }

  private boolean isItalyTrip(List<TripRoute> tripRoutes) {
    // 通过trip_routes中的destination_city判断，如果有Rome、Florence、Milan、Venice，返回true，否则false
    if (CollectionUtils.isEmpty(tripRoutes)) {
      return false;
    }
    
    // 定义意大利城市列表
    Set<String> italyCities = Set.of("Rome", "Florence", "Milan", "Venice");
    
    return tripRoutes.stream()
        .anyMatch(route -> route != null && 
            italyCities.stream().anyMatch(city -> 
                city.equalsIgnoreCase(route.getDestination_city())));
  }

  private GeneratePlanParam mockSZXThailandGeneratePlanParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("SZX");
    param.setLocation_code("CN");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-20");
    param.setTrip_days(5);
    param.setAdult_number(2);
    param.setChild_number(0);
    param.setRoom_quantity(1);
    param.setBudgets("15000");
    param.setCurrency("USD");
    param.setTrip_routes(buildSZXThailandOneWayTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildSZXThailandOneWayTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(2, "Phuket", "TH", "HKT"));
    tripRouteParams.add(buildRouteTrip(3, "Krabi", "TH", "KBV"));
    return tripRouteParams;
  }

  private GeneratePlanParam mockSZXThailandChiangMaiGeneratePlanParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("SZX");
    param.setLocation_code("CN");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-20");
    param.setTrip_days(5);
    param.setAdult_number(2);
    param.setChild_number(0);
    param.setRoom_quantity(1);
    param.setBudgets("15000");
    param.setCurrency("USD");
    param.setTrip_routes(buildSZXThailandChiangMaiOneWayTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildSZXThailandChiangMaiOneWayTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(2, "Phuket", "TH", "HKT"));
    tripRouteParams.add(buildRouteTrip(1, "Chiang Mai", "TH", "CNX"));
    tripRouteParams.add(buildRouteTrip(2, "Krabi", "TH", "KBV"));
    return tripRouteParams;
  }

  private GeneratePlanParam mockSZXItalyGeneratePlanParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("SZX");
    param.setLocation_code("CN");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-20");
    param.setTrip_days(5);
    param.setAdult_number(1);
    param.setChild_number(0);
    param.setRoom_quantity(1);
    param.setBudgets("15000");
    param.setCurrency("USD");
    param.setTrip_routes(buildSZXItalyOneWayTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildSZXItalyOneWayTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(2, "Milan", "IT", "MXP"));
    tripRouteParams.add(buildRouteTrip(3, "Rome", "IT", "FCO"));
    return tripRouteParams;
  }

  private GeneratePlanParam mockGeneratePlanParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("FLR.json");
    param.setLocation_code("IT");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-07");
    param.setTrip_days(7);
    param.setAdult_number(1);
    param.setChild_number(0);
    param.setRoom_quantity(1);
    param.setBudgets("50000");
    param.setCurrency("CNY");
    param.setTrip_routes(buildOneWayTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildOneWayTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(5, "Rome", "IT", "FCO"));
    tripRouteParams.add(buildRouteTrip(5, "Florence", "IT", "FLR"));
    tripRouteParams.add(buildRouteTrip(4, "Venice", "IT", "VCE"));
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
   * @param planResult        计划结果
   * @param invocationContext 调用上下文
   * @return 事件流
   */
  private Flowable<Event> createPlanEvents(TripRoutePlanResult planResult, InvocationContext invocationContext) {
    List<Event> events = new ArrayList<>();
    long currentTimeMillis = Instant.now().toEpochMilli();

    // 第一个事件：调整后的计划结果事件
    try {
      String planResultJson = JsonUtil.toJson(planResult);

      // 生成planResultId并缓存planResultJson
      PlanResultCacheService cacheService = getPlanResultCacheService();
      String planResultId = cacheService.cachePlanResult(planResultJson);

      // 第二个event的parts设置为planResultId
      Content planContent = Content.builder().role("planner").parts(Lists.newArrayList(Part.fromText(planResultId))).build();
      Event planEvent = Event.builder()
          .invocationId(invocationContext.invocationId())
          .author("agent")
          .content(planContent)
          .timestamp(currentTimeMillis + 1) // 稍微延后一毫秒确保顺序
          .build();
      events.add(planEvent);

      log.info("Created plan result event with planResultId: {}, cached {} daily plans",
          planResultId, planResult.getDailyPlans() != null ? planResult.getDailyPlans().size() : 0);


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

    // 第二个事件：摘要事件（逻辑不变）
    if (planResult.getSummary() != null && !planResult.getSummary().isEmpty()) {
      Content summaryContent = Content.builder().role("agent")
          .parts(Lists.newArrayList(Part.fromText(planResult.getSummary() + " If you wish to adjust the itinerary or need me to provide additional details, please let me know."))).build();
      Event summaryEvent = Event.builder()
          .invocationId(invocationContext.invocationId())
          .author("agent")
          .content(summaryContent)
          .timestamp(currentTimeMillis)
          .build();
      events.add(summaryEvent);

      log.info("Created summary event with content length: {}", planResult.getSummary().length());
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

  private List<TripRoute> extract(String text) {
    if (StringUtils.isNotEmpty(text)) {
      String pref = null;
      try {
        if (text.contains("------")) {
          String[] tt = text.split("------");
          pref = tt[0];
          text = tt[1];
        }
        text = text.replace("```json", "").replace("```", "");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        List<TripRoute> tripRoutes = mapper.readValue(text, new TypeReference<List<TripRoute>>() {});
        return tripRoutes;
      }catch (Throwable e){
        log.error("extract parse error {}", ExceptionUtils.getStackTrace(e));
      }
    }
    return null;
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

}
