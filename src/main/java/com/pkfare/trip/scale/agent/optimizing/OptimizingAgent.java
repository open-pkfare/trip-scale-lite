package com.pkfare.trip.scale.agent.optimizing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.agents.BaseAgent;
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
import com.pkfare.trip.scale.model.dto.BriefTripRoutePlan;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

@Slf4j
public class OptimizingAgent extends BaseAgent {

  public static final String PREFIX = "------";
  private static final String CURRENT_AGENT = "trip_optimizing_agent";
  private static final String BASE_AGENT = "trip_base_agent";
  public static final String OPTIMIZER_ROLE = "optimizer";
  private static BaseAgent INSTANCE;

  private static OptimizingAgent optimizingAgent;
  private static ApplicationContext applicationContext;

  public static void setApplicationContext(ApplicationContext context) {
    applicationContext = context;
  }

  public OptimizingAgent() {
    super(CURRENT_AGENT, "Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.",
        Lists.newArrayList(INSTANCE),
        null,
        null);
  }

  public static BaseAgent instance() {
    if (null == INSTANCE) {
      Instruction instruction = new Provider(rc -> {
        TripRoutePlanResult result = (TripRoutePlanResult) rc.state().get("plan_result");
        BriefTripRoutePlan briefTripRoutePlan = new BriefTripRoutePlan(result);
        //        BriefTripRoutePlan briefTripRoutePlan = mockDailyPlans();
        String prompt = StringUtils.replace(OptimizingPrompt.PROMPT, "{{trip_plan}}",
            JsonUtil.toJson(briefTripRoutePlan));
        return Single.just(prompt);
      });
      INSTANCE = LlmAgent.builder()
          .name(BASE_AGENT)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to help user to optimize a travel plans, including adjusting flights, hotels and activities, etc.")
          .instruction(instruction)
          .build();
      optimizingAgent = new OptimizingAgent();
    }
    return optimizingAgent;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {

    final String[] content = new String[1];
    invocationContext.agent().findAgent(BASE_AGENT).runAsync(invocationContext).blockingForEach(event -> {
      content[0] = event.content().get().text();
    });

    Optional<String> optional = parse(Objects.requireNonNull(content[0]));
    if (optional.isPresent()) {
      return doOptimize(invocationContext, optional.get());
    }
    Event errorEvent = Event.builder()
        .invocationId(invocationContext.invocationId())
        .author("agent")
        .content(Content.builder().role("agent").parts(Lists.newArrayList(Part.fromText(content[0]))).build())
        .timestamp(Instant.now().toEpochMilli())
        .build();
    return Flowable.just(errorEvent);
  }

  private Flowable<Event> doOptimize(InvocationContext invocationContext, String param) {
    log.info("the optimize param is: {}", param);
    try {
      TripDemand tripDemand = (TripDemand) invocationContext.session().state().get("trip_demand");
      List<TripRoute> tripRoutes = (List<TripRoute>) invocationContext.session().state().get("trip_route");
      TripRoutePlanResult tripRoutePlanResult = (TripRoutePlanResult) invocationContext.session().state().get("plan_result");
      if (tripDemand == null || tripRoutes == null || tripRoutePlanResult == null) {
        log.error("TripDemand, TripRoutes or TripRoutePlanResult is null in session state");
        return Flowable.error(new IllegalStateException("Missing required data in session"));
      }
      if ("[]".equals(param)) {
        return Flowable.empty();
      } else {
        JsonNode adjustPlanParams = JsonUtil.toJsonNode(param);
        GeneratePlanParam planParam = buildGeneratePlanParam(tripDemand, tripRoutes);
        long start = System.currentTimeMillis();
        TripRoutePlanResult planResult = applicationContext.getBean(TripPlanAdjustService.class)
            .adjustPlan(planParam, tripRoutePlanResult, adjustPlanParams);
        log.info("*******************************************************Time taken to optimizing plan: {} ms", System.currentTimeMillis() - start);
        log.info("optimize trip plan with status: {}", planResult.getStatus());
        return createPlanEvents(planResult, invocationContext);
      }
    } catch (Exception e) {
      log.error("Error in optimizingAgent runAsyncImpl", e);
      return Flowable.error(e);
    }
  }

  private Optional<String> parse(String text) {
    if (!text.contains(PREFIX)) {
      return Optional.empty();
    }
    String[] tt = text.split(PREFIX);
    text = tt[1].replace("```json", "").replace("```", "");
    return Optional.of(text);
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
      Content planContent = Content.builder().role(OPTIMIZER_ROLE).parts(Lists.newArrayList(Part.fromText(planResultJson))).build();
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

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return Flowable.empty();
  }

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());
    Session session =
        runner
            .sessionService()
            .createSession(CURRENT_AGENT, "test_optimizing")
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("test_optimizing", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

  private static BriefTripRoutePlan mockDailyPlans() {
    BriefTripRoutePlan briefTripRoutePlan = new BriefTripRoutePlan();
    String flights = "[{\"oneWay\":false,\"total\":\"2415.00\",\"currency\":\"CNY\",\"itineraries\":[{\"duration\":\"PT55M\","
        + "\"segments\":[{\"departure\":\"FLR\",\"departureTime\":\"2025-10-01T11:20:00\",\"arrival\":\"FCO\",\"arrivalTerminal\":\"1\","
        + "\"arrivalTime\":\"2025-10-01T12:15:00\",\"arrivalGeo\":{\"latitude\":41.79362,\"longitude\":12.2525},\"carrierCode\":\"AZ\","
        + "\"number\":\"1678\",\"duration\":\"PT55M\"}]},{\"duration\":\"PT1H40M\",\"segments\":[{\"departure\":\"CDG\","
        + "\"departureTerminal\":\"2F\",\"departureTime\":\"2025-10-07T17:55:00\",\"departureGeo\":{\"latitude\":49.01278,\"longitude\":2.55},"
        + "\"arrival\":\"FLR\",\"arrivalTime\":\"2025-10-07T19:35:00\",\"carrierCode\":\"AF\",\"number\":\"1766\",\"duration\":\"PT1H40M\"}]}]}]";

    List<FlightInfo> flightInfos = JsonUtil.fromJson(flights, new TypeReference<List<FlightInfo>>() {
    });
    briefTripRoutePlan.setPreferredFlights(flightInfos);
    String dailyPlan = "[{\"date\":\"2025-10-01\",\"cityCode\":\"FCO\",\"cityName\":\"Rome\",\"preferredHotel\":{\"hotelId\":\"BWFCO336\","
        + "\"dupeId\":\"700193275\",\"offerId\":\"23O43KCF30\",\"hotelName\":\"Best Western Hotel Rome Airport\",\"cityCode\":\"FCO\","
        + "\"cityName\":\"Rome\",\"checkInDate\":\"2025-10-01\",\"checkOutDate\":\"2025-10-04\",\"nights\":3,\"totalPrice\":495.0,"
        + "\"currency\":\"EUR\",\"latitude\":41.77279,\"longitude\":12.2415,\"address\":\"\",\"preferred\":true},\"alternativeHotels\":[],"
        + "\"activities\":[{\"activityId\":\"139507783\",\"name\":\"Vatican & Sistine Chapel: Family-Friendly Private Half-Day Tour\","
        + "\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":1043.0,\"currency\":\"EUR\",\"latitude\":41.9076932,\"longitude\":12.452998,"
        + "\"pictures\":[]},{\"activityId\":\"339666\",\"name\":\"Rome  - Walking Tour  - 'Roma Barocca'  - 5 ore  \","
        + "\"description\":\"<p>Meet&nbsp;by your local guide and enjoy a guided tour of Rome - the Eternal City (approx. 3 hours). The tour begins"
        + " at the Trevi Fountain, which has become an iconic spot thanks to the movie &lsquo;La Dolce Vita&rsquo;, directed by Federico Fellini. "
        + "The tour will take you to the streets of the old town, visiting famous sites such as the Pantheon; the Palazzo Madama, which houses the "
        + "Senate of the Italian Republic (entrance not included); and Navona Square, which was built on the ruins of the Domitian Circus. Once the"
        + " tour finishes you can enjoy the rest of the afternoon at leisure.&nbsp;</p>\\r\\n<p>Possibility to reserve half day or full day&nbsp;"
        + "</p>\",\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":80.0,\"currency\":\"EUR\",\"latitude\":41.900996383806347,\"longitude\":12"
        + ".484257137605939,\"pictures\":[\"https://cdn.bookingkit"
        + ".de/vendor_images/c6fdb94b5e41f0c79d38643809bafd9a/detail/1609693666michele-bitetto-2y6ojwauKJI-unsplash.jpg\"]},"
        + "{\"activityId\":\"168748\",\"name\":\"Shared Transfer From the Civitavecchia Port to Fiumicino airport\",\"cityCode\":\"Rome\","
        + "\"rating\":0.0,\"price\":87.0,\"currency\":\"EUR\",\"latitude\":41.9027835,\"longitude\":12.4963655,\"pictures\":[]}]},"
        + "{\"date\":\"2025-10-02\",\"cityCode\":\"FCO\",\"cityName\":\"Rome\",\"preferredHotel\":{\"hotelId\":\"BWFCO336\","
        + "\"dupeId\":\"700193275\",\"offerId\":\"23O43KCF30\",\"hotelName\":\"Best Western Hotel Rome Airport\",\"cityCode\":\"FCO\","
        + "\"cityName\":\"Rome\",\"checkInDate\":\"2025-10-01\",\"checkOutDate\":\"2025-10-04\",\"nights\":3,\"totalPrice\":495.0,"
        + "\"currency\":\"EUR\",\"latitude\":41.77279,\"longitude\":12.2415,\"address\":\"\",\"preferred\":true},\"alternativeHotels\":[],"
        + "\"activities\":[{\"activityId\":\"139512097\",\"name\":\"Vatican Museums & Sistine Chapel: Guided Evening Tour\",\"cityCode\":\"Rome\","
        + "\"rating\":0.0,\"price\":75.0,\"currency\":\"EUR\",\"latitude\":41.90705860000001,\"longitude\":12.4533292,\"pictures\":[]},"
        + "{\"activityId\":\"139544124\",\"name\":\"Tour SEMI privado  del Coliseo, Foro y Palatino SPAGOLO\",\"description\":\"<p>Patrimonio de la"
        + " Humanidad y una de las Siete Maravillas del Mundo.&nbsp;Conoced el Coliseo de la forma m&aacute;s personalizada posible&nbsp;con este "
        + "tour privado en el que contar&eacute;is con un gu&iacute;a en exclusiva. Solo&nbsp;para ti y tu pareja, familia o amigos"
        + ".</p>\\r\\n<p>&iquest;Prefer&iacute;s profundizar a&uacute;n m&aacute;s en la historia del Coliseo? En este recorrido, accederemos a la "
        + "Arena del Coliseo de Roma: el lugar donde combat&iacute;an a muerte los gladiadores. &iexcl;Sentir&eacute;is que hac&eacute;is un "
        + "aut&eacute;ntico viaje en el tiempo! Veremos las gradas del anfiteatro desde una perspectiva &uacute;nica y, despu&eacute;s, nos "
        + "dirigiremos al Foro y los yacimientos del Palatino.</p>\",\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":10.0,\"currency\":\"EUR\","
        + "\"latitude\":41.8913422,\"longitude\":12.4911664,\"pictures\":[]},{\"activityId\":\"76570685\",\"name\":\"Unexpected Rome: cycle between"
        + " Villa Borghese and Villa Ada, discover the magic of the Coppedè district\",\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":66.0,"
        + "\"currency\":\"EUR\",\"latitude\":41.9162519,\"longitude\":12.4696146,\"pictures\":[]}]},{\"date\":\"2025-10-03\",\"cityCode\":\"FCO\","
        + "\"cityName\":\"Rome\",\"preferredHotel\":{\"hotelId\":\"BWFCO336\",\"dupeId\":\"700193275\",\"offerId\":\"23O43KCF30\","
        + "\"hotelName\":\"Best Western Hotel Rome Airport\",\"cityCode\":\"FCO\",\"cityName\":\"Rome\",\"checkInDate\":\"2025-10-01\","
        + "\"checkOutDate\":\"2025-10-04\",\"nights\":3,\"totalPrice\":495.0,\"currency\":\"EUR\",\"latitude\":41.77279,\"longitude\":12.2415,"
        + "\"address\":\"\",\"preferred\":true},\"alternativeHotels\":[],\"activities\":[{\"activityId\":\"194756\",\"name\":\"Viva Vivaldi: The "
        + "Four Seasons in Piazza Navona\",\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":23.0,\"currency\":\"EUR\",\"latitude\":41.8991796,"
        + "\"longitude\":12.4732651,\"pictures\":[]},{\"activityId\":\"139545727\",\"name\":\"Rome Private Tour with Certified Guide and Must-See "
        + "Spots\",\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":325.0,\"currency\":\"EUR\",\"latitude\":41.9027835,\"longitude\":12.4963655,"
        + "\"pictures\":[]},{\"activityId\":\"167855\",\"name\":\"Semi-Private Vatican Tour with Sistine Chapel and St. Peter’s Basilica\","
        + "\"cityCode\":\"Rome\",\"rating\":0.0,\"price\":111.0,\"currency\":\"EUR\",\"latitude\":41.90705860000001,\"longitude\":12.4533292,"
        + "\"pictures\":[]}]}]";

    List<BriefDailyRoutePlan> tripDayInfos = JsonUtil.fromJson(dailyPlan, new TypeReference<List<BriefDailyRoutePlan>>() {
    });
    briefTripRoutePlan.setDailyPlans(tripDayInfos);
    return briefTripRoutePlan;
  }
}
