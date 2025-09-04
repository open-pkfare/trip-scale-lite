package com.pkfare.trip.scale.agent.optimizing;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.Instruction.Provider;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.dto.TripRoute;
import com.pkfare.trip.scale.model.dto.BriefDailyRoutePlan;
import com.pkfare.trip.scale.model.dto.TripDayInfo;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

@Slf4j
public class DailyOptimizingAgent extends BaseAgent {

  private static final String NAME = "trip_daily_optimizing_agent";

  private static BaseAgent INSTANCE;

  // 静态引用ApplicationContext，用于获取Spring Bean
  private static ApplicationContext applicationContext;

  public DailyOptimizingAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public DailyOptimizingAgent() {
    super(NAME, "Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.",
        null,
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE) {
      Instruction instruction = new Provider(rc -> {
        TripRoutePlanResult result = (TripRoutePlanResult) rc.state().get("plan_result");
        TripDayInfo chooseDayPlan = (TripDayInfo) rc.state().get("chose_day_plan");
//        DailyRoutePlan dailyRoutePlan = new DailyRoutePlan();
//        if (Objects.nonNull(chooseDayPlan) && chooseDayPlan.getDayOfTrip() != -1
//            && chooseDayPlan.getDayOfTrip() <= result.getDailyPlans().size()) {
//          dailyRoutePlan = result.getDailyPlans().get(chooseDayPlan.getDayOfTrip() - 1);
//        }
         BriefDailyRoutePlan briefPlan = mockDailyPlans();
//        BriefDailyRoutePlan briefPlan = new BriefDailyRoutePlan(dailyRoutePlan);
        String prompt = StringUtils.replace(DailyOptimizingPrompt.PROMPT, "{{daily_route_plan}}", JsonUtil.toJson(briefPlan));
        return Single.just(prompt);
      });
      INSTANCE = LlmAgent.builder()
          .name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("A client that helps travelers optimize their itinerary plans and output solutions.")
          .instruction(instruction)
          .build();
    }
    return INSTANCE;
  }

  public static void setApplicationContext(ApplicationContext context) {
    applicationContext = context;
  }


  /**
   * 获取GeneratePlanService实例
   */
  private TripPlanAdjustService getTripPlanAdjustService() {
    if (applicationContext == null) {
      throw new IllegalStateException("ApplicationContext not set. Please call setApplicationContext() first.");
    }
    return applicationContext.getBean(TripPlanAdjustService.class);
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    log.info("Starting PlanningAgent runAsyncImpl");

    try {
      // todo 空数组则不做调整
      JsonNode adjustPlanParams = null;
      // 从会话状态中获取数据
      TripDemand tripDemand = (TripDemand) invocationContext.session().state().get("trip_demand");
      List<TripRoute> tripRoutes = (List<TripRoute>) invocationContext.session().state().get("trip_route");
      TripRoutePlanResult tripRoutePlanResult = (TripRoutePlanResult) invocationContext.session().state().get("plan_result");
      if (tripDemand == null || tripRoutes == null || tripRoutePlanResult == null) {
        log.error("TripDemand, TripRoutes or TripRoutePlanResult is null in session state");
        return Flowable.error(new IllegalStateException("Missing required data in session"));
      }
      // 通过tripDemand和tripRoutes构建GeneratePlanParam
      GeneratePlanParam param = buildGeneratePlanParam(tripDemand, tripRoutes);

      TripPlanAdjustService tripPlanAdjustService = getTripPlanAdjustService();
      long start = System.currentTimeMillis();
      TripRoutePlanResult planResult = tripPlanAdjustService.adjustPlan(param, tripRoutePlanResult, null);
      log.info("*******************************************************Time taken to optimizing plan: {} ms", System.currentTimeMillis() - start);
      log.info("optimize trip plan with status: {}", planResult.getStatus());

      // 拆成两个event：TripRoutePlanResult.summary 摘要是单独的一个Event，TripRoutePlanResult是一个单独的event
      return createPlanEvents(planResult, invocationContext);
    } catch (Exception e) {
      log.error("Error in PlanningAgent runAsyncImpl", e);
      return Flowable.error(e);
    }
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

    // 第一个事件：摘要事件
    if (planResult.getSummary() != null && !planResult.getSummary().isEmpty()) {
      Content summaryContent = Content.builder().role("agent").parts(Lists.newArrayList(Part.fromText(planResult.getSummary()))).build();
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
      String planResultJson = JsonUtil.toJson(planResult);
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

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());
    Session session =
        runner
            .sessionService()
            .createSession(NAME, "test_daily_optimizing")
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("test_daily_optimizing", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

  private static BriefDailyRoutePlan mockDailyPlans() {
    String dailyRoutePlanJson = "{\n"
        + "    \"date\": \"2025-10-01\",\n"
        + "    \"cityCode\": \"FCO\",\n"
        + "    \"cityName\": \"Rome\",\n"
        + "    \"preferredHotel\": {\n"
        + "        \"hotelId\": \"BWFCO336\",\n"
        + "        \"dupeId\": \"700193275\",\n"
        + "        \"offerId\": \"23O43KCF30\",\n"
        + "        \"hotelName\": \"Best Western Hotel Rome Airport\",\n"
        + "        \"cityCode\": \"FCO\",\n"
        + "        \"cityName\": \"Rome\",\n"
        + "        \"checkInDate\": \"2025-10-01\",\n"
        + "        \"checkOutDate\": \"2025-10-04\",\n"
        + "        \"nights\": 3,\n"
        + "        \"totalPrice\": 495.0,\n"
        + "        \"currency\": \"EUR\",\n"
        + "        \"latitude\": 41.77279,\n"
        + "        \"longitude\": 12.2415,\n"
        + "        \"address\": \"\",\n"
        + "        \"preferred\": true\n"
        + "    },\n"
        + "    \"alternativeHotels\": [],\n"
        + "    \"activities\": [\n"
        + "        {\n"
        + "            \"activityId\": \"139507783\",\n"
        + "            \"name\": \"Vatican & Sistine Chapel: Family-Friendly Private Half-Day Tour\",\n"
        + "            \"cityCode\": \"Rome\",\n"
        + "            \"rating\": 0.0,\n"
        + "            \"price\": 1043.0,\n"
        + "            \"currency\": \"EUR\",\n"
        + "            \"latitude\": 41.9076932,\n"
        + "            \"longitude\": 12.452998,\n"
        + "            \"pictures\": [\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzLzQ5NjVlOTg3LTQxNzktNDgyMi1iZjVkLWNkNDA1OWY2ZjQzNiIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\",\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzLzAwOTJlNWJmLTA4NzktNDg0NC1iYmQ0LTUyNjNlNTI1YWY3ZiIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\"\n"
        + "            ]\n"
        + "        },\n"
        + "        {\n"
        + "            \"activityId\": \"339666\",\n"
        + "            \"name\": \"Rome  - Walking Tour  - 'Roma Barocca'  - 5 ore  \",\n"
        + "            \"description\": \"<p>Meet&nbsp;by your local guide and enjoy a guided tour of Rome - the Eternal City (approx. 3 hours). "
        + "The tour begins at the Trevi Fountain, which has become an iconic spot thanks to the movie &lsquo;La Dolce Vita&rsquo;, directed by "
        + "Federico Fellini. The tour will take you to the streets of the old town, visiting famous sites such as the Pantheon; the Palazzo Madama,"
        + " which houses the Senate of the Italian Republic (entrance not included); and Navona Square, which was built on the ruins of the "
        + "Domitian Circus. Once the tour finishes you can enjoy the rest of the afternoon at leisure.&nbsp;</p>\\r\\n<p>Possibility to reserve "
        + "half day or full day&nbsp;</p>\",\n"
        + "            \"cityCode\": \"Rome\",\n"
        + "            \"rating\": 0.0,\n"
        + "            \"price\": 80.0,\n"
        + "            \"currency\": \"EUR\",\n"
        + "            \"latitude\": 41.900996383806347,\n"
        + "            \"longitude\": 12.484257137605939,\n"
        + "            \"pictures\": [\n"
        + "                \"https://cdn.bookingkit.de/vendor_images/c6fdb94b5e41f0c79d38643809bafd9a/detail/1609693666michele-bitetto-2y6ojwauKJI"
        + "-unsplash.jpg\"\n"
        + "            ]\n"
        + "        },\n"
        + "        {\n"
        + "            \"activityId\": \"168748\",\n"
        + "            \"name\": \"Shared Transfer From the Civitavecchia Port to Fiumicino airport\",\n"
        + "            \"cityCode\": \"Rome\",\n"
        + "            \"rating\": 0.0,\n"
        + "            \"price\": 87.0,\n"
        + "            \"currency\": \"EUR\",\n"
        + "            \"latitude\": 41.9027835,\n"
        + "            \"longitude\": 12.4963655,\n"
        + "            \"pictures\": [\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzL2E2NmM1MTQ3LWY5NjQtMTFlYi04MDFmLTA2YjgxYWQ0YzU3OSIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\",\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzL2E2NmM1NzliLWY5NjQtMTFlYi04MDFmLTA2YjgxYWQ0YzU3OSIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\",\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzL2E2NmM1NTZlLWY5NjQtMTFlYi04MDFmLTA2YjgxYWQ0YzU3OSIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\",\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzL2E2NmM0NmM0LWY5NjQtMTFlYi04MDFmLTA2YjgxYWQ0YzU3OSIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\",\n"
        + "                \"https://images.holibob"
        + ".tech"
        +
        "/eyJrZXkiOiJwcm9kdWN0SW1hZ2VzL2E2NmM1OWE4LWY5NjQtMTFlYi04MDFmLTA2YjgxYWQ0YzU3OSIsImVkaXRzIjp7InJlc2l6ZSI6eyJmaXQiOiJjb3ZlciIsIndpZHRoIjoxOTIwLCJoZWlnaHQiOjEwODB9fX0=\"\n"
        + "            ]\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    return JsonUtil.fromJson(dailyRoutePlanJson, BriefDailyRoutePlan.class);
  }
}
