package com.pkfare.trip.scale.agent.booking;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.web.config.DevConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.agent.inspiration.DemandPrompt;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.TripDemand;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailyRoutePlan;
import com.pkfare.trip.scale.plan.service.response.DailySchedule;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
public class BookingAgent extends BaseAgent {

  private static String NAME = "booking_agent";

  private static BookingAgent INSTANCE;

  private static BaseAgent SUMMARY_AGENT;

  InMemoryRunner runner = new InMemoryRunner(SUMMARY_AGENT);

  private static Map<String, JSON> ROAD_BOOK_MAP = Maps.newConcurrentMap();

  private static final String path = "http://localhost:8080/roadbook/";

  @Setter
  private DevConfig devConfig;

  public BookingAgent(String name, String description, List<? extends BaseAgent> subAgents,
      List<BeforeAgentCallback> beforeAgentCallback,
      List<AfterAgentCallback> afterAgentCallback) {
    super(name, description, subAgents, beforeAgentCallback, afterAgentCallback);
  }

  public BookingAgent() {
    super("p_booking_agent", "Agent to help user book the trip and generate roadbook.",
        Lists.newArrayList(SUMMARY_AGENT),
        null,
        null);
  }

  public static synchronized BookingAgent instance() {
    if (null == INSTANCE) {
      SUMMARY_AGENT = LlmAgent.builder().name(NAME)
          .model(GoogleConfig.GEMINI_2_5_FLASH)
          .description("Agent to help user to summarize trip items.")
          .instruction(BookingPrompt.SUMMARY_PROMPT).build();

      INSTANCE = new BookingAgent();
    }
    return INSTANCE;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    log.info("current agent: booking");
    TripRoutePlanResult tripRoutePlanResult = (TripRoutePlanResult) invocationContext.session().state().get("plan_result");
    Map<LocalDate, JSONObject> summaries = summarize(invocationContext, tripRoutePlanResult);
    JSON json = generateRoadBook(tripRoutePlanResult, summaries);
    ROAD_BOOK_MAP.put(invocationContext.session().id(), json);
    Event event = Event.builder().author("agent")
        .content(Content.builder().role("agent").parts(Lists.newArrayList(Part.fromText("Booking successful, and i had prepared the road book for you~!"))).build())
        .build();

    Event event1 = Event.builder().author("agent")
        .content(Content.builder().role("planner").parts(Lists.newArrayList(Part.fromText("booking"))).build())
        .build();
    List<Event> events = Lists.newArrayList();
    events.add(event);
    events.add(event1);
    return Flowable.fromIterable(events);
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return null;
  }

  private Map<LocalDate, JSONObject> summarize(InvocationContext invocationContext, TripRoutePlanResult tripRoutePlanResult) {
    Map<LocalDate, JSONObject> all = Maps.newConcurrentMap();

    List<CompletableFuture<Void>> futures = tripRoutePlanResult.getDailyPlans().stream()
        .map(dailyPlan -> CompletableFuture.runAsync(() -> {
          Map<String, String> map = dailyPlan.getActivities().stream()
              .collect(Collectors.toMap(ActivityInfo::getActivityId, ai -> ai.getName() + " " + ai.getDescription()));

          Content content = Content.fromParts(Part.fromText("here are activities: \n" + JSON.toJSONString(map)));

          Session session = runner.sessionService().createSession(NAME, UUID.randomUUID().toString()).blockingGet();
          runner.runAsync(session.userId(), session.id(), content)
              .blockingForEach(event -> {
                //parse daily arrangement and tips, construct the road book
                String text = event.content().get().text();
                if (text.contains("------")){
                  text = text.split("------")[1];
                }
                text = text.replace("```json","").replace("```","");
                JSONObject object = JSON.parseObject(text);
                object.put("date", dailyPlan.getDate().toString());
                object.put("city", dailyPlan.getCityName());
                all.put(dailyPlan.getDate(), object);
              });
        }))
        .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return all;
  }

  private JSON generateRoadBook(TripRoutePlanResult tripRoutePlanResult, Map<LocalDate, JSONObject> summaries) {
    JSONArray all = new JSONArray();
    List<DailyRoutePlan> dailyPlans = tripRoutePlanResult.getDailyPlans();
    for (DailyRoutePlan dailyPlan : dailyPlans) {
      JSONObject daySum = summaries.get(dailyPlan.getDate());
      JSONArray arrangements = daySum.getJSONArray("arrangement");
      List<ActivityInfo> activities = dailyPlan.getActivities();
      Map<String,ActivityInfo> activityInfoMap = activities.stream().collect(Collectors.toMap(ActivityInfo::getActivityId, a->a));
      arrangements.forEach(o -> {
        JSONObject arrangement = (JSONObject)o;
        ActivityInfo activityInfo = activityInfoMap.get(arrangement.getString("item_id"));
        if (null == activityInfo){
          return;
        }
        arrangement.put("name", activityInfo.getName());
        arrangement.put("type", "activity");
        arrangement.put("lat", activityInfo.getLatitude());
        arrangement.put("lan",activityInfo.getLongitude());
        arrangement.put("img",CollectionUtils.isEmpty(activityInfo.getPictures())?"":activityInfo.getPictures().getFirst());
      });
      daySum.put("routes",dailyPlan.getRoutes());

      all.add(daySum);
    }
    return all;
  }

  public JSON roadbook(String id){
    return ROAD_BOOK_MAP.get(id);
  }

  public static void main(String[] args) {
    InMemoryRunner runner = new InMemoryRunner(instance());
    Session session =
        runner
            .sessionService()
            .createSession("p_booking_agent", "test_inspiration")
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (true) {
        System.out.print("\nYou > ");
        String userInput = scanner.nextLine();

        if ("quit".equalsIgnoreCase(userInput)) {
          break;
        }

        Content userMsg = Content.fromParts(Part.fromText(userInput));
        Flowable<Event> events = runner.runAsync("test_inspiration", session.id(), userMsg);

        System.out.print("\nTripScale > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
      }
    }
  }

  private static TripRoutePlanResult mockDailyPlans() {
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

    DailyRoutePlan dailyRoutePlan = JSON.parseObject(dailyRoutePlanJson, DailyRoutePlan.class);
    TripRoutePlanResult tripRoutePlanResult = new TripRoutePlanResult();
    tripRoutePlanResult.setDailyPlans(Lists.newArrayList(dailyRoutePlan));
    return tripRoutePlanResult;
  }

}
