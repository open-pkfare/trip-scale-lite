package com.pkfare.trip.scale.service.external.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest.Waypoint;
import com.google.maps.GeoApiContext;
import com.google.maps.model.*;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.*;
import com.pkfare.trip.scale.util.JsonUtil;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Google AI服务，用于生成路线规划
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class GoogleAiService {

  private final GeoApiContext geoApiContext;

  private final Executor routeCalculationExecutor;

  private static final int MAX_CONCURRENT_REQUESTS = 10;

  // 路线缓存：起点经纬度_终点经纬度 -> RouteSegment
  private final Map<String, RouteSegment> routeCache = new ConcurrentHashMap<>();

  private static final double CACHE_DISTANCE_THRESHOLD_M = 100.0; // 100米内认为是同一位置

  public GoogleAiService() {
    this.geoApiContext = new GeoApiContext.Builder()
        .apiKey(GoogleConfig.GOOGLE_API_KEY)
        .build();

    this.routeCalculationExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS,
        r -> {
          Thread t = new Thread(r, "GoogleDirections-" + System.currentTimeMillis());
          t.setDaemon(true);
          return t;
        });
  }

  /**
   * 基于航班、酒店、景点活动等信息生成每日路线规划
   *
   * @param planInfo 计划信息，包含航班、酒店、景点活动等信息（包含经纬度）
   * @return 旅行路线规划结果
   */
  public TripRoutePlanResult generateAiPlan(SubmitAiPlanInfo planInfo) {
    log.info("Starting daily route planning generation");

    if (planInfo == null || planInfo.getGeneratePlanParam() == null) {
      log.error("Plan info or generate plan param is null");
      return createErrorResult("Plan info is invalid");
    }

    try {
      // 1. 按城市和日期组织数据
      Map<String, CityPlanData> cityPlanMap = organizePlanDataByCity(planInfo);

      // 2. 生成每日路线规划
      List<DailyRoutePlan> dailyPlans = generateDailyRoutePlans(cityPlanMap, planInfo.getGeneratePlanParam());

      // 3. 构建最终结果
      TripRoutePlanResult result = new TripRoutePlanResult();
      result.setStatus("SUCCESS");
      result.setDailyPlans(dailyPlans);
      result.setPreferredFlights(planInfo.getFlightMap().getOrDefault("preferred", Lists.newArrayList()));
      result.setAlternativeFlights(planInfo.getFlightMap().getOrDefault("alternative", Lists.newArrayList()));

      // 计算总距离和总时间
      long totalDistance = dailyPlans.stream()
          .filter(plan -> plan.getTotalDistance() != null)
          .mapToLong(DailyRoutePlan::getTotalDistance)
          .sum();
      long totalDuration = dailyPlans.stream()
          .filter(plan -> plan.getTotalDuration() != null)
          .mapToLong(DailyRoutePlan::getTotalDuration)
          .sum();

      result.setTotalDistance(totalDistance);
      result.setTotalDuration(totalDuration);
      result.setSummary(generateSummary(dailyPlans));
      log.info("Daily route planning generation completed successfully");
      return result;

    } catch (Exception e) {
      log.error("Failed to generate daily route planning", e);
      return createErrorResult("Failed to generate route planning: " + e.getMessage());
    }
  }

  /**
   * 按城市组织计划数据
   */
  private Map<String, CityPlanData> organizePlanDataByCity(SubmitAiPlanInfo planInfo) {
    Map<String, CityPlanData> cityPlanMap = new HashMap<>();
    GeneratePlanParam param = planInfo.getGeneratePlanParam();

    // 处理每个城市的行程
    for (TripRouteParam routeParam : param.getTrip_routes()) {
      String cityCode = routeParam.getLocation_code();
      CityPlanData cityData = cityPlanMap.computeIfAbsent(cityCode, k -> new CityPlanData());

      cityData.setCityCode(cityCode);
      cityData.setCityName(routeParam.getDestination_city());
      cityData.setStayDays(routeParam.getStay_days());

      // 添加该城市的酒店（创建新列表，避免共享引用）
      if (planInfo.getHotelInfos() != null) {
        List<HotelInfo> cityHotels = planInfo.getHotelInfos().stream()
            .filter(hotel -> cityCode.equals(hotel.getCityCode()))
            .collect(Collectors.toList());
        cityData.setHotels(new ArrayList<>(cityHotels));
      }

      // 添加该城市的活动（创建新列表，避免共享引用）
      if (planInfo.getActivityInfos() != null) {
        List<ActivityInfo> cityActivities = planInfo.getActivityInfos().stream()
            .filter(activity -> cityData.getCityName().equals(activity.getCityCode()))
            .collect(Collectors.toList());
        cityData.setActivities(new ArrayList<>(cityActivities));
      }
    }

    return cityPlanMap;
  }

  /**
   * 生成每日路线规划
   */
  private List<DailyRoutePlan> generateDailyRoutePlans(Map<String, CityPlanData> cityPlanMap,
      GeneratePlanParam param) throws Exception {
    List<CompletableFuture<DailyRoutePlan>> futures = new ArrayList<>();
    LocalDate currentDate = LocalDate.parse(param.getStart_period());

    for (TripRouteParam routeParam : param.getTrip_routes()) {
      String cityCode = routeParam.getLocation_code();
      CityPlanData cityData = cityPlanMap.get(cityCode);

      if (cityData == null) {
        continue;
      }

      // 为该城市的每一天生成路线规划
      for (int day = 0; day < routeParam.getStay_days(); day++) {
        //DailyRoutePlan dailyPlan = generateSingleDayPlan(cityData, currentDate.plusDays(day), day);
        //dailyPlans.add(dailyPlan);
        final LocalDate planDate = currentDate.plusDays(day);
        final int dayIndex = day;
        CompletableFuture<DailyRoutePlan> future = CompletableFuture.supplyAsync(() -> {
          try {
            return generateSingleDayPlanOptimized(cityData, planDate, dayIndex);
          } catch (Exception e) {
            log.error("Failed to generate plan for day {} in city {}", dayIndex, cityData.getCityName(), e);
            return createEmptyDayPlan(cityData, planDate);
          }
        }, routeCalculationExecutor);

        futures.add(future);
      }

      currentDate = currentDate.plusDays(routeParam.getStay_days());
    }

    // 等待所有任务完成
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    return allFutures.thenApply(v ->
        futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList())
    ).get();
  }

  private DailyRoutePlan generateSingleDayPlanOptimized(CityPlanData cityData, LocalDate date, int dayIndex) throws Exception {
    DailyRoutePlan dailyPlan = new DailyRoutePlan();
    dailyPlan.setDate(date);
    dailyPlan.setCityCode(cityData.getCityCode());
    dailyPlan.setCityName(cityData.getCityName());

    // 选择酒店（通常选择第一个）
    if (cityData.getHotels() != null && !cityData.getHotels().isEmpty()) {
      dailyPlan.setPreferredHotel(cityData.getHotels().get(0));
      dailyPlan.setAlternativeHotels(cityData.getHotels().subList(1, cityData.getHotels().size()));
    }

    // 选择当日活动（平均分配活动到各天）
    List<ActivityInfo> dayActivities = selectDayActivities(cityData.getActivities(), dayIndex, cityData.getStayDays());
    dailyPlan.setActivities(dayActivities);

    // 优化版路线生成
    generateRoutesLocations(dailyPlan, dayActivities);
    // generateRoutesOptimized(dailyPlan, dayActivities);
    return dailyPlan;
  }

  private void generateRoutesLocations(DailyRoutePlan dailyPlan, List<ActivityInfo> dayActivities) {
    if (dailyPlan.getPreferredHotel() == null || dayActivities == null || dayActivities.isEmpty()) {
      dailyPlan.setRoutes(new ArrayList<>());
      dailyPlan.setTotalDistance(0L);
      dailyPlan.setTotalDuration(0L);
      return;
    }

    // 批量并发计算路线
    List<LocationPoint> waypoints = generateRouteLocationPoints(dailyPlan.getPreferredHotel(), dayActivities);
    dailyPlan.setWaypoints(waypoints);

    dailyPlan.setTotalDistance(0L);
    dailyPlan.setTotalDuration(0L);
    dailyPlan.setNotes(generateDayNotes(dayActivities, 0, 0));
  }

  public void generateRoutesOptimized(DailyRoutePlan dailyPlan, List<ActivityInfo> dayActivities) throws Exception {
    if (dailyPlan.getPreferredHotel() == null || dayActivities == null || dayActivities.isEmpty()) {
      dailyPlan.setRoutes(new ArrayList<>());
      dailyPlan.setTotalDistance(0L);
      dailyPlan.setTotalDuration(0L);
      return;
    }

    // 批量并发计算路线
    List<RouteSegment> routes = generateRoutesBatch(dailyPlan.getPreferredHotel(), dayActivities);
    dailyPlan.setRoutes(routes);

    // 计算总距离和时间
    long totalDistance = routes.stream()
        .filter(route -> route.getDistance() != null)
        .mapToLong(RouteSegment::getDistance)
        .sum();
    long totalDuration = routes.stream()
        .filter(route -> route.getDuration() != null)
        .mapToLong(RouteSegment::getDuration)
        .sum();

    dailyPlan.setTotalDistance(totalDistance);
    dailyPlan.setTotalDuration(totalDuration);
    dailyPlan.setNotes(generateDayNotes(dayActivities, totalDistance, totalDuration));
  }


  private List<RouteSegment> generateRoutesBatch(HotelInfo hotel, List<ActivityInfo> activities) throws Exception {
    if (hotel == null || activities == null || activities.isEmpty()) {
      return new ArrayList<>();
    }

    // 创建位置点列表：酒店 -> 活动1 -> 活动2 -> ... -> 酒店
    List<LocationPoint> waypoints = new ArrayList<>();
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    // 并发计算所有路线段
    List<CompletableFuture<RouteSegment>> routeFutures = new ArrayList<>();

    for (int i = 0; i < waypoints.size() - 1; i++) {
      LocationPoint start = waypoints.get(i);
      LocationPoint end = waypoints.get(i + 1);

      CompletableFuture<RouteSegment> future = CompletableFuture.supplyAsync(() -> calculateRouteWithCache(start, end), routeCalculationExecutor);

      routeFutures.add(future);
    }

    // 等待所有路线计算完成
    CompletableFuture<Void> allRoutes = CompletableFuture.allOf(routeFutures.toArray(new CompletableFuture[0]));

    return allRoutes.thenApply(v ->
        routeFutures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList())
    ).get();
  }

  private List<LocationPoint> generateRouteLocationPoints(HotelInfo hotel, List<ActivityInfo> activities) {
    if (hotel == null || activities == null || activities.isEmpty()) {
      return new ArrayList<>();
    }

    // 创建位置点列表：酒店 -> 活动1 -> 活动2 -> ... -> 酒店
    List<LocationPoint> waypoints = new ArrayList<>();
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    return waypoints;
  }


  /**
   * 带缓存的路线计算
   */
  private RouteSegment calculateRouteWithCache(LocationPoint start, LocationPoint end) {
    // 生成缓存键
    String cacheKey = generateCacheKey(start, end);

    // 先检查缓存
    RouteSegment cachedRoute = routeCache.get(cacheKey);
    if (cachedRoute != null) {
      log.debug("Cache hit for route: {} -> {}", start.getName(), end.getName());
      return cloneRouteSegment(cachedRoute, start, end);
    }

    // 检查是否有相近位置的缓存
    RouteSegment nearbyRoute = findNearbyRoute(start, end);
    if (nearbyRoute != null) {
      log.debug("Nearby cache hit for route: {} -> {}", start.getName(), end.getName());
      routeCache.put(cacheKey, nearbyRoute);
      return cloneRouteSegment(nearbyRoute, start, end);
    }

    // 缓存未命中，调用API
    RouteSegment newRoute = calculateRoute(start, end);
    if (newRoute != null) {
      routeCache.put(cacheKey, newRoute);
      log.debug("Route calculated and cached: {} -> {}", start.getName(), end.getName());
    }

    return newRoute;
  }

  /**
   * 查找相近位置的缓存路线
   */
  private RouteSegment findNearbyRoute(LocationPoint start, LocationPoint end) {
    for (Map.Entry<String, RouteSegment> entry : routeCache.entrySet()) {
      String[] parts = entry.getKey().split("_");
      if (parts.length == 2) {
        String[] startCoords = parts[0].split(",");
        String[] endCoords = parts[1].split(",");

        if (startCoords.length == 2 && endCoords.length == 2) {
          try {
            double cachedStartLat = Double.parseDouble(startCoords[0]);
            double cachedStartLng = Double.parseDouble(startCoords[1]);
            double cachedEndLat = Double.parseDouble(endCoords[0]);
            double cachedEndLng = Double.parseDouble(endCoords[1]);

            double startDistance = calculateDistance(start.getLatitude(), start.getLongitude(),
                cachedStartLat, cachedStartLng);
            double endDistance = calculateDistance(end.getLatitude(), end.getLongitude(),
                cachedEndLat, cachedEndLng);

            if (startDistance <= CACHE_DISTANCE_THRESHOLD_M && endDistance <= CACHE_DISTANCE_THRESHOLD_M) {
              return entry.getValue();
            }
          } catch (NumberFormatException e) {
            // 忽略格式错误的缓存键
          }
        }
      }
    }
    return null;
  }

  /**
   * 计算两点间距离（米）
   */
  private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
    final int R = 6371000; // 地球半径（米）

    double latDistance = Math.toRadians(lat2 - lat1);
    double lngDistance = Math.toRadians(lng2 - lng1);

    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
  }

  /**
   * 克隆路线段并更新名称
   */
  private RouteSegment cloneRouteSegment(RouteSegment original, LocationPoint start, LocationPoint end) {
    RouteSegment clone = new RouteSegment();
    clone.setStartName(start.getName());
    clone.setStartLocation(createGeoInfo(start.getLatitude(), start.getLongitude()));
    clone.setEndName(end.getName());
    clone.setEndLocation(createGeoInfo(end.getLatitude(), end.getLongitude()));
    clone.setDistance(original.getDistance());
    clone.setDuration(original.getDuration());
    clone.setTravelMode(original.getTravelMode());
    clone.setOverview(original.getOverview());
    //clone.setRoutesJson(original.getRoutesJson());
    return clone;
  }

  /**
   * 生成缓存键
   */
  private String generateCacheKey(LocationPoint start, LocationPoint end) {
    return String.format("%.6f,%.6f_%.6f,%.6f",
        start.getLatitude(), start.getLongitude(),
        end.getLatitude(), end.getLongitude());
  }

  /**
   * 生成单日路线规划
   */
  private DailyRoutePlan generateSingleDayPlan(CityPlanData cityData, LocalDate date, int dayIndex) throws Exception {
    DailyRoutePlan dailyPlan = new DailyRoutePlan();
    dailyPlan.setDate(date);
    dailyPlan.setCityCode(cityData.getCityCode());
    dailyPlan.setCityName(cityData.getCityName());

    // 选择酒店（通常选择第一个）
    if (cityData.getHotels() != null && !cityData.getHotels().isEmpty()) {
      dailyPlan.setPreferredHotel(cityData.getHotels().get(0));
      dailyPlan.setAlternativeHotels(cityData.getHotels().subList(1, cityData.getHotels().size()));
    }

    // 选择当日活动（平均分配活动到各天）
    List<ActivityInfo> dayActivities = selectDayActivities(cityData.getActivities(), dayIndex, cityData.getStayDays());
    dailyPlan.setActivities(dayActivities);

    generateRoutes(dailyPlan, dayActivities);
    return dailyPlan;
  }

  public void generateRoutes(DailyRoutePlan dailyPlan, List<ActivityInfo> dayActivities) throws Exception {
    // 生成路线
    List<RouteSegment> routes = generateRoutes(dailyPlan.getPreferredHotel(), dayActivities);
    dailyPlan.setRoutes(routes);

    // 计算总距离和时间
    long totalDistance = routes.stream()
        .filter(route -> route.getDistance() != null)
        .mapToLong(RouteSegment::getDistance)
        .sum();
    long totalDuration = routes.stream()
        .filter(route -> route.getDuration() != null)
        .mapToLong(RouteSegment::getDuration)
        .sum();

    dailyPlan.setTotalDistance(totalDistance);
    dailyPlan.setTotalDuration(totalDuration);
    dailyPlan.setNotes(generateDayNotes(dayActivities, totalDistance, totalDuration));
  }

  /**
   * 选择当日活动
   * 修复：避免ConcurrentModificationException，先创建副本再排序
   */
  private List<ActivityInfo> selectDayActivities(List<ActivityInfo> allActivities, int dayIndex, int totalDays) {
    if (allActivities == null || allActivities.isEmpty()) {
      return new ArrayList<>();
    }

    // 创建活动列表的副本，避免并发修改异常
    List<ActivityInfo> activitiesCopy = new ArrayList<>(allActivities);

    // 按评分排序（现在是对副本进行排序，线程安全）
    activitiesCopy.sort((a, b) -> Double.compare(b.getRating(), a.getRating()));

    // 平均分配活动到各天，每天最多3个活动
    int activitiesPerDay = Math.min(3, Math.max(1, activitiesCopy.size() / totalDays));
    int startIndex = dayIndex * activitiesPerDay;
    int endIndex = Math.min(startIndex + activitiesPerDay, activitiesCopy.size());

    if (startIndex >= activitiesCopy.size()) {
      return new ArrayList<>();
    }

    // 返回子列表，使用已排序的副本
    return new ArrayList<>(activitiesCopy.subList(startIndex, endIndex));
  }

  /**
   * 生成路线段
   */
  private List<RouteSegment> generateRoutes(HotelInfo hotel, List<ActivityInfo> activities) throws Exception {
    List<RouteSegment> routes = new ArrayList<>();

    if (hotel == null || activities == null || activities.isEmpty()) {
      return routes;
    }

    // 创建位置点列表：酒店 -> 活动1 -> 活动2 -> ... -> 酒店
    List<LocationPoint> waypoints = new ArrayList<>();
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotelName(), hotel.getLatitude(), hotel.getLongitude()));

    // 生成每段路线
    for (int i = 0; i < waypoints.size() - 1; i++) {
      LocationPoint start = waypoints.get(i);
      LocationPoint end = waypoints.get(i + 1);

      RouteSegment segment = calculateRoute(start, end);
      if (segment != null) {
        routes.add(segment);
      }
    }

    return routes;
  }

  /**
   * 计算两点间路线
   */
  private RouteSegment calculateRoute(LocationPoint start, LocationPoint end) {
    try {
      LatLng startLatLng = new LatLng(start.getLatitude(), start.getLongitude());
      LatLng endLatLng = new LatLng(end.getLatitude(), end.getLongitude());

      DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
          .origin(startLatLng)
          .destination(endLatLng)
          .mode(TravelMode.DRIVING)
          .language("en")
          .region("us")
          .await();

      if (result.routes != null && result.routes.length > 0) {
        DirectionsRoute route = result.routes[0];
        DirectionsLeg leg = route.legs[0];

        RouteSegment segment = new RouteSegment();
        segment.setStartName(start.getName());
        segment.setStartLocation(createGeoInfo(start.getLatitude(), start.getLongitude()));
        segment.setEndName(end.getName());
        segment.setEndLocation(createGeoInfo(end.getLatitude(), end.getLongitude()));
        segment.setDistance(leg.distance.inMeters);
        segment.setDuration(leg.duration.inSeconds);
        segment.setTravelMode("DRIVING");

        // 提取步骤说明
        List<String> steps = new ArrayList<>();
        //for (DirectionsStep step : leg.steps) {
        //    steps.add(step.htmlInstructions);
        //}
        segment.setSteps(steps);
        segment.setOverview(route.summary);
        // segment.setRoutesJson(JsonUtil.toJson(result.routes));

        return segment;
      }
    } catch (Exception e) {
      log.error("Failed to calculate route from {} to {}", start.getName(), end.getName(), e);
    }

    return null;
  }

  /**
   * 创建GeoInfo对象
   */
  private GeoInfo createGeoInfo(double latitude, double longitude) {
    GeoInfo geoInfo = new GeoInfo();
    geoInfo.setLatitude(latitude);
    geoInfo.setLongitude(longitude);
    return geoInfo;
  }

  /**
   * 生成当日备注
   */
  private String generateDayNotes(List<ActivityInfo> activities, long totalDistance, long totalDuration) {
    StringBuilder notes = new StringBuilder();
    notes.append("Today's itinerary includes ").append(activities.size()).append(" attractions, ");
    notes.append("total distance approximately ").append(String.format("%.1f", totalDistance / 1000.0)).append(" km, ");
    notes.append("total travel time approximately ").append(totalDuration / 60).append(" minutes. ");

    if (!activities.isEmpty()) {
      notes.append("Main attractions: ");
      for (int i = 0; i < activities.size(); i++) {
        if (i > 0) {
          notes.append(", ");
        }
        notes.append(activities.get(i).getName());
      }
      notes.append(".");
    }

    return notes.toString();
  }

  /**
   * 生成总结
   */
  public String generateSummary(List<DailyRoutePlan> dailyPlans) {
    StringBuilder summary = new StringBuilder();
    summary.append("This trip spans ").append(dailyPlans.size()).append(" days, ");

    Set<String> cities = dailyPlans.stream()
        .map(DailyRoutePlan::getCityName)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    summary.append("covering ").append(cities.size()).append(" cities: ");
    summary.append(String.join(", ", cities));

    long totalActivities = 0;
    for (DailyRoutePlan plan : dailyPlans) {
      if (plan.getActivities() != null) {
        totalActivities += plan.getActivities().size();
      }
    }
    summary.append(", with a total of ").append(totalActivities).append(" attractions and activities scheduled.");

    return summary.toString();
  }

  /**
   * 创建错误结果
   */
  private TripRoutePlanResult createErrorResult(String errorMessage) {
    TripRoutePlanResult result = new TripRoutePlanResult();
    result.setStatus("ERROR");
    result.setErrorMessage(errorMessage);
    result.setDailyPlans(new ArrayList<>());
    return result;
  }


  private DailyRoutePlan createEmptyDayPlan(CityPlanData cityData, LocalDate date) {
    DailyRoutePlan plan = new DailyRoutePlan();
    plan.setDate(date);
    plan.setCityCode(cityData.getCityCode());
    plan.setCityName(cityData.getCityName());
    plan.setRoutes(new ArrayList<>());
    plan.setActivities(new ArrayList<>());
    return plan;
  }
}
