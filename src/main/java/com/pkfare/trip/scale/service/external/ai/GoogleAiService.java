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
import com.pkfare.trip.scale.service.plan.dto.DailyActivityPlan;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
  
  @Value("${trip.plan.ai.timeout-seconds:300}")
  private int aiTimeoutSeconds;
  
  @Value("${trip.plan.ai.route-calculation-timeout-seconds:300}")
  private int routeCalculationTimeoutSeconds;

  private static final int MAX_CONCURRENT_REQUESTS = 50; // 增加线程池大小以避免死锁

  // 路线缓存：起点经纬度_终点经纬度 -> RouteSegment
  private final Map<String, RouteSegment> routeCache = new ConcurrentHashMap<>();

  private static final double CACHE_DISTANCE_THRESHOLD_M = 100.0; // 100米内认为是同一位置

  public GoogleAiService() {
    this.geoApiContext = new GeoApiContext.Builder()
        .apiKey(GoogleConfig.GOOGLE_API_KEY)
        .build();

    // 使用ThreadPoolExecutor以便更好地控制线程池行为
    this.routeCalculationExecutor = new java.util.concurrent.ThreadPoolExecutor(
        MAX_CONCURRENT_REQUESTS / 2, // 核心线程数
        MAX_CONCURRENT_REQUESTS,     // 最大线程数
        60L, java.util.concurrent.TimeUnit.SECONDS, // 空闲线程存活时间
        new java.util.concurrent.LinkedBlockingQueue<>(100), // 队列大小
        r -> {
          Thread t = new Thread(r, "GoogleDirections-" + System.currentTimeMillis());
          t.setDaemon(true);
          return t;
        },
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
    );
  }

  /**
   * 基于已分配的每日活动生成路线规划
   *
   * @param planInfo 计划信息，包含每日活动分配
   * @return 旅行路线规划结果
   */
  public TripRoutePlanResult generateAiPlanWithDailyAllocation(SubmitAiPlanInfo planInfo) {
    log.info("Starting route planning generation with pre-allocated daily activities");

    if (planInfo == null || planInfo.getGeneratePlanParam() == null || planInfo.getDailyActivityPlans() == null) {
      log.error("Plan info, generate plan param, or daily activity plans is null");
      return createErrorResult("Plan info or daily activity plans is invalid");
    }

    try {
      // 1. 直接使用已分配的每日活动，无需重新组织
      List<DailyActivityPlan> dailyActivityPlans = planInfo.getDailyActivityPlans();
      
      // 2. 生成每日路线规划（基于已分配的活动）
      List<DailyRoutePlan> dailyPlans = generateDailyRoutePlansFromAllocation(
          dailyActivityPlans, planInfo.getHotelInfos(), planInfo.getGeneratePlanParam());

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
      log.info("Route planning generation with daily allocation completed successfully");
      return result;

    } catch (Exception e) {
      log.error("Failed to generate route planning with daily allocation", e);
      return createErrorResult("Failed to generate route planning: " + e.getMessage());
    }
  }

  /**
   * 基于航班、酒店、景点活动等信息生成每日路线规划（兼容旧接口）
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
            .filter(hotel -> cityCode.equals(hotel.getHotel().getCityCode()))
            .collect(Collectors.toList());
        cityData.setHotels(new ArrayList<>(cityHotels));
      }

      // 从每日活动计划中提取该城市的活动，按日期组织到Map中（创建新列表，避免共享引用）
      if (planInfo.getDailyActivityPlans() != null && !planInfo.getDailyActivityPlans().isEmpty()) {
        Map<String, List<ActivityInfo>> dailyActivitiesMap = extractCityActivitiesByDate(
            planInfo.getDailyActivityPlans(), cityCode);
        cityData.setActivities(dailyActivitiesMap);
      }
    }

    return cityPlanMap;
  }

  /**
   * 从每日活动计划中提取指定城市的活动，按日期组织到Map中
   * 
   * @param dailyActivityPlans 每日活动计划列表
   * @param cityCode 城市代码
   * @return 按日期组织的活动Map（线程安全，避免共享引用）
   */
  private Map<String, List<ActivityInfo>> extractCityActivitiesByDate(
      List<DailyActivityPlan> dailyActivityPlans, String cityCode) {
    
    // 使用ConcurrentHashMap来确保线程安全
    Map<String, List<ActivityInfo>> dailyActivitiesMap = new ConcurrentHashMap<>();
    
    // 并行处理每日计划，提取指定城市的活动
    dailyActivityPlans.parallelStream()
        .filter(dailyPlan -> cityCode.equals(dailyPlan.getCityCode()))
        .filter(dailyPlan -> dailyPlan.getActivities() != null)
        .forEach(dailyPlan -> {
          String dateKey = dailyPlan.getDate().toString(); // LocalDate转换为字符串
          
          // 创建该日期的活动列表（深度拷贝，避免共享引用）
          List<ActivityInfo> dayActivities = dailyPlan.getActivities().stream()
              .map(this::createActivityCopy)
              .collect(Collectors.toCollection(ArrayList::new));
          
          // 线程安全地添加到Map中
          dailyActivitiesMap.put(dateKey, dayActivities);
        });
    
    return dailyActivitiesMap;
  }

  /**
   * 创建ActivityInfo的深度拷贝，避免共享引用
   * 
   * @param original 原始活动信息
   * @return 新的ActivityInfo实例
   */
  private ActivityInfo createActivityCopy(ActivityInfo original) {
    ActivityInfo copy = new ActivityInfo();
    copy.setActivityId(original.getActivityId());
    copy.setName(original.getName());
    copy.setDescription(original.getDescription());
    copy.setCityCode(original.getCityCode());
    copy.setRating(original.getRating());
    copy.setPrice(original.getPrice());
    copy.setCurrency(original.getCurrency());
    copy.setLatitude(original.getLatitude());
    copy.setLongitude(original.getLongitude());
    copy.setType(original.getType());
    copy.setPictures(original.getPictures() != null ? new ArrayList<>(original.getPictures()) : null);
    return copy;
  }

  /**
   * 基于已分配的每日活动生成路线规划
   */
  private List<DailyRoutePlan> generateDailyRoutePlansFromAllocation(
      List<DailyActivityPlan> dailyActivityPlans, List<HotelInfo> hotelInfos, GeneratePlanParam param) throws Exception {
    
    // 添加线程池状态监控
    logThreadPoolStatus("Before generating daily route plans");
    
    List<CompletableFuture<DailyRoutePlan>> futures = new ArrayList<>();
    
    for (DailyActivityPlan dailyActivityPlan : dailyActivityPlans) {
      CompletableFuture<DailyRoutePlan> future = CompletableFuture.supplyAsync(() -> {
        try {
          return generateSingleDayPlanFromAllocation(dailyActivityPlan, hotelInfos);
        } catch (Exception e) {
          log.error("Failed to generate route plan for day {} in city {}", 
              dailyActivityPlan.getDate(), dailyActivityPlan.getCityName(), e);
          return createEmptyDayPlanFromAllocation(dailyActivityPlan);
        }
      }, routeCalculationExecutor);
      
      futures.add(future);
    }
    
    // 等待所有任务完成，添加超时处理
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    
    try {
      logThreadPoolStatus("Waiting for daily route plans completion");
      List<DailyRoutePlan> result = allFutures.thenApply(v ->
          futures.stream()
              .map(CompletableFuture::join)
              .collect(Collectors.toList())
      ).get(aiTimeoutSeconds, TimeUnit.SECONDS);
      
      logThreadPoolStatus("After daily route plans completion");
      return result;
    } catch (TimeoutException e) {
      log.warn("Daily route plan generation from allocation timed out after {} seconds, returning partial results", aiTimeoutSeconds);
      logThreadPoolStatus("After timeout occurred");
      
      // 取消未完成的任务
      futures.forEach(future -> future.cancel(true));
      // 返回已完成的计划
      return futures.stream()
          .filter(CompletableFuture::isDone)
          .filter(future -> !future.isCancelled() && !future.isCompletedExceptionally())
          .map(CompletableFuture::join)
          .collect(Collectors.toList());
    }
  }

  /**
   * 生成每日路线规划（兼容旧接口）
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

    // 等待所有任务完成，添加超时处理
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      return allFutures.thenApply(v ->
          futures.stream()
              .map(CompletableFuture::join)
              .collect(Collectors.toList())
      ).get(aiTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.warn("Daily route plan generation timed out after {} seconds, returning partial results", aiTimeoutSeconds);
      // 取消未完成的任务
      futures.forEach(future -> future.cancel(true));
      // 返回已完成的计划
      return futures.stream()
          .filter(CompletableFuture::isDone)
          .filter(future -> !future.isCancelled() && !future.isCompletedExceptionally())
          .map(CompletableFuture::join)
          .collect(Collectors.toList());
    }
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

    // 获取当日已分配的活动
    String dateKey = date.toString();
    List<ActivityInfo> dayActivities = cityData.getActivities() != null ? 
        cityData.getActivities().getOrDefault(dateKey, new ArrayList<>()) : new ArrayList<>();
    dailyPlan.setActivities(dayActivities);

    // 优化版路线生成
    generateRoutesOptimized(dailyPlan, dayActivities);
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

    logThreadPoolStatus("Before route batch calculation");

    // 创建位置点列表：酒店 -> 活动1 -> 活动2 -> ... -> 酒店
    List<LocationPoint> waypoints = new ArrayList<>();
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

    // 并发计算所有路线段
    List<CompletableFuture<RouteSegment>> routeFutures = new ArrayList<>();

    for (int i = 0; i < waypoints.size() - 1; i++) {
      LocationPoint start = waypoints.get(i);
      LocationPoint end = waypoints.get(i + 1);

      CompletableFuture<RouteSegment> future = CompletableFuture.supplyAsync(() -> calculateRouteWithCache(start, end), routeCalculationExecutor);

      routeFutures.add(future);
    }

    // 等待所有路线计算完成，添加超时处理
    CompletableFuture<Void> allRoutes = CompletableFuture.allOf(routeFutures.toArray(new CompletableFuture[0]));

    try {
      return allRoutes.thenApply(v ->
          routeFutures.stream()
              .map(CompletableFuture::join)
              .filter(Objects::nonNull)
              .collect(Collectors.toList())
      ).get(routeCalculationTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.warn("Route calculation timed out after {} seconds, returning partial results", routeCalculationTimeoutSeconds);
      // 取消未完成的任务
      routeFutures.forEach(future -> future.cancel(true));
      // 返回已完成的路线
      return routeFutures.stream()
          .filter(CompletableFuture::isDone)
          .filter(future -> !future.isCancelled() && !future.isCompletedExceptionally())
          .map(CompletableFuture::join)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }
  }

  private List<LocationPoint> generateRouteLocationPoints(HotelInfo hotel, List<ActivityInfo> activities) {
    if (hotel == null || activities == null || activities.isEmpty()) {
      return new ArrayList<>();
    }

    // 创建位置点列表：酒店 -> 活动1 -> 活动2 -> ... -> 酒店
    List<LocationPoint> waypoints = new ArrayList<>();
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

    return waypoints;
  }


  /**
   * 带缓存的路线计算
   */
  private RouteSegment calculateRouteWithCache(LocationPoint start, LocationPoint end) {
    log.info("calculateRouteWithCache,start:{},end:{}",start,end);
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

    // 获取当日已分配的活动
    String dateKey = date.toString();
    List<ActivityInfo> dayActivities = cityData.getActivities() != null ? 
        cityData.getActivities().getOrDefault(dateKey, new ArrayList<>()) : new ArrayList<>();
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
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

    for (ActivityInfo activity : activities) {
      waypoints.add(new LocationPoint(activity.getName(), activity.getLatitude(), activity.getLongitude()));
    }

    // 回到酒店
    waypoints.add(new LocationPoint(hotel.getHotel().getName(), hotel.getHotel().getLatitude(), hotel.getHotel().getLongitude()));

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
    log.info("calculateRoute,start:{},end:{}",start,end);
    // 根据地理位置智能选择交通模式优先级
    TravelMode[] travelModes = getOptimalTravelModes(start, end);
    
    for (TravelMode mode : travelModes) {
      try {
        log.debug("Attempting {} route from {} to {}", mode, start.getName(), end.getName());
        
        LatLng startLatLng = new LatLng(start.getLatitude(), start.getLongitude());
        LatLng endLatLng = new LatLng(end.getLatitude(), end.getLongitude());

        // 根据地区选择合适的region参数
        String region = isVeniceArea(start) || isVeniceArea(end) ? "it" : "us";

        DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
            .origin(startLatLng)
            .destination(endLatLng)
            .mode(mode)
            .language("en")
            .region(region)
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
          segment.setTravelMode(mode.toString());

          // 提取步骤说明
          List<String> steps = new ArrayList<>();
          //for (DirectionsStep step : leg.steps) {
          //    steps.add(step.htmlInstructions);
          //}
          segment.setSteps(steps);
          segment.setOverview(route.summary);
          //segment.setRoutesJson(JsonUtil.toJson(result.routes));

          log.info("Successfully calculated {} route from {} to {}", mode, start.getName(), end.getName());
          return segment;
        }
      } catch (Exception e) {
        log.debug("Failed to calculate {} route from {} to {}: {}", mode, start.getName(), end.getName(), e.getMessage());
        // 继续尝试下一个交通模式
      }
    }
    
    // 所有交通模式都失败，创建估算路线
    log.warn("All travel modes failed for route from {} to {}, creating estimated route", start.getName(), end.getName());
    return createEstimatedRoute(start, end);
  }

  /**
   * 根据地理位置获取最优交通模式顺序
   */
  private TravelMode[] getOptimalTravelModes(LocationPoint start, LocationPoint end) {
    boolean startInVenice = isVeniceArea(start);
    boolean endInVenice = isVeniceArea(end);
    
    if (startInVenice || endInVenice) {
      // Venice地区：优先公共交通（包含水上巴士），然后步行，最后驾车
      log.debug("Venice area detected, prioritizing TRANSIT and WALKING modes");
      return new TravelMode[]{TravelMode.TRANSIT, TravelMode.WALKING, TravelMode.DRIVING};
    } else {
      // 其他地区：优先驾车，然后公共交通，最后步行
      return new TravelMode[]{TravelMode.DRIVING, TravelMode.TRANSIT, TravelMode.WALKING};
    }
  }

  /**
   * 检查坐标是否在Venice地区
   */
  private boolean isVeniceArea(LocationPoint point) {
    // Venice及其岛屿的大致范围：纬度 45.3-45.5，经度 12.2-12.4
    double lat = point.getLatitude();
    double lng = point.getLongitude();
    return lat >= 45.3 && lat <= 45.5 && lng >= 12.2 && lng <= 12.4;
  }

  /**
   * 创建估算路线（当所有API调用都失败时的回退方案）
   */
  private RouteSegment createEstimatedRoute(LocationPoint start, LocationPoint end) {
    log.info("Creating estimated route from {} to {}", start.getName(), end.getName());
    
    // 计算直线距离
    double distanceMeters = calculateDistance(start.getLatitude(), start.getLongitude(), 
        end.getLatitude(), end.getLongitude());
    
    // 根据地区和距离估算时间
    boolean isVeniceRoute = isVeniceArea(start) || isVeniceArea(end);
    double avgSpeedKmh;
    String travelMode;
    String overview;
    
    if (isVeniceRoute) {
      // Venice地区：考虑水上交通和步行
      avgSpeedKmh = 12.0; // 水上巴士 + 步行的平均速度
      travelMode = "WATER_TRANSIT";
      overview = "Estimated route via Venice water transport and walking";
    } else {
      // 其他地区：一般城市交通
      avgSpeedKmh = 25.0; // 城市交通平均速度
      travelMode = "ESTIMATED";
      overview = "Estimated route based on straight-line distance";
    }
    
    // 计算估算时间（秒）
    long estimatedDurationSeconds = Math.round((distanceMeters / 1000.0) / avgSpeedKmh * 3600);
    // 最少5分钟
    estimatedDurationSeconds = Math.max(estimatedDurationSeconds, 300);
    
    RouteSegment segment = new RouteSegment();
    segment.setStartName(start.getName());
    segment.setStartLocation(createGeoInfo(start.getLatitude(), start.getLongitude()));
    segment.setEndName(end.getName());
    segment.setEndLocation(createGeoInfo(end.getLatitude(), end.getLongitude()));
    segment.setDistance(Math.round(distanceMeters));
    segment.setDuration(estimatedDurationSeconds);
    segment.setTravelMode(travelMode);
    
    List<String> steps = new ArrayList<>();
    if (isVeniceRoute) {
      steps.add("Take vaporetto (water bus) or walk through Venice");
      steps.add("Estimated route - actual water transport schedules may vary");
    } else {
      steps.add("Estimated route based on geographical distance");
      steps.add("Actual route and travel time may vary significantly");
    }
    segment.setSteps(steps);
    segment.setOverview(overview);
    
    return segment;
  }

  /**
   * 记录线程池状态
   */
  private void logThreadPoolStatus(String context) {
    if (routeCalculationExecutor instanceof java.util.concurrent.ThreadPoolExecutor) {
      java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) routeCalculationExecutor;
      log.info("{} - ThreadPool Status: Active={}, Pool={}, Queue={}, Completed={}", 
          context,
          tpe.getActiveCount(),
          tpe.getPoolSize(), 
          tpe.getQueue().size(),
          tpe.getCompletedTaskCount());
    }
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


  /**
   * 基于已分配的每日活动生成单日路线规划
   */
  private DailyRoutePlan generateSingleDayPlanFromAllocation(
      DailyActivityPlan dailyActivityPlan, List<HotelInfo> hotelInfos) throws Exception {
    log.info("generateSingleDayPlanFromAllocation dailyActivityPlan:{}",dailyActivityPlan.getDate());
    DailyRoutePlan dailyPlan = new DailyRoutePlan();
    dailyPlan.setDate(dailyActivityPlan.getDate());
    dailyPlan.setCityCode(dailyActivityPlan.getCityCode());
    dailyPlan.setCityName(dailyActivityPlan.getCityName());
    
    // 直接使用已分配的活动
    dailyPlan.setActivities(dailyActivityPlan.getActivities());
    
    // 选择该城市的酒店
    if (hotelInfos != null && !hotelInfos.isEmpty()) {
      List<HotelInfo> cityHotels = hotelInfos.stream()
          .filter(hotel -> dailyActivityPlan.getCityCode().equals(hotel.getHotel().getCityCode()))
          .collect(Collectors.toList());
      
      if (!cityHotels.isEmpty()) {
        dailyPlan.setPreferredHotel(cityHotels.get(0));
        if (cityHotels.size() > 1) {
          dailyPlan.setAlternativeHotels(cityHotels);
        }
      }
    }
    
    // 生成路线规划
    if (dailyActivityPlan.getActivities() != null && !dailyActivityPlan.getActivities().isEmpty()) {
      generateRoutesOptimized(dailyPlan, dailyActivityPlan.getActivities());
    } else {
      dailyPlan.setRoutes(new ArrayList<>());
      dailyPlan.setTotalDistance(0L);
      dailyPlan.setTotalDuration(0L);
    }
    
    return dailyPlan;
  }
  
  /**
   * 创建空的每日路线规划（基于已分配活动）
   */
  private DailyRoutePlan createEmptyDayPlanFromAllocation(DailyActivityPlan dailyActivityPlan) {
    DailyRoutePlan plan = new DailyRoutePlan();
    plan.setDate(dailyActivityPlan.getDate());
    plan.setCityCode(dailyActivityPlan.getCityCode());
    plan.setCityName(dailyActivityPlan.getCityName());
    plan.setRoutes(new ArrayList<>());
    plan.setActivities(dailyActivityPlan.getActivities() != null ? 
        dailyActivityPlan.getActivities() : new ArrayList<>());
    return plan;
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
