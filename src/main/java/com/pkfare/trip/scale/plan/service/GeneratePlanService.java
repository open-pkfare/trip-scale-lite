package com.pkfare.trip.scale.plan.service;

import com.pkfare.trip.scale.model.dto.FlightSearchResult;
import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.CityLocationInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.service.plan.ActivitySearchService;
import com.pkfare.trip.scale.service.plan.FlightSearchService;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import com.pkfare.trip.scale.service.plan.LocationSearchService;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.JsonUtil;
import com.pkfare.trip.scale.util.ValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 旅行计划生成服务
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class GeneratePlanService {

  @Autowired
  private FlightSearchService flightSearchService;

  @Autowired
  private HotelSearchService hotelSearchService;

  @Autowired
  private ActivitySearchService activitySearchService;

  @Autowired
  private GoogleAiService googleAiService;

  @Autowired
  private LocationSearchService locationSearchService;

  @Autowired
  @Qualifier("tripPlanExecutor")
  private Executor tripPlanExecutor;

  /**
   * 生成旅行计划主入口 - 并发优化版本
   *
   * @param param 生成计划参数
   * @return 完整的旅行计划
   */
  public TripRoutePlanResult generatePlan(GeneratePlanParam param) {
    log.info("Starting concurrent trip plan generation for origin: {}, destinations: {}",
        param.getOrigin(), param.getTrip_routes().size());
    long start = System.currentTimeMillis();
    
    try {
      // 1. 参数验证
      validateParams(param);

      // 2. 计算 preciseTravel 和 roundTrip
      boolean preciseTravel = calculatePreciseTravel(param);
      boolean roundTrip = false;// calculateRoundTrip(param);

      log.info("Trip configuration: preciseTravel={}, roundTrip={}", preciseTravel, roundTrip);
      logThreadPoolStatus();

      // 3. 如果不是精确时间，先搜索最便宜航班的日期
      FlightSearchResult dateResult = null;
      if (!preciseTravel) {
        long dateSearchStart = System.currentTimeMillis();
        dateResult = flightSearchService.searchFlightDates(param, roundTrip);
        log.info("Flight date search completed in {} ms", System.currentTimeMillis() - dateSearchStart);
      }

      // 4. 并发执行独立的搜索任务
      long concurrentStart = System.currentTimeMillis();
      CompletableFuture<ConcurrentSearchResult> concurrentResult = 
          executeConcurrentSearches(param, dateResult, preciseTravel, roundTrip);
      
      ConcurrentSearchResult searchResult = concurrentResult.get();
      log.info("All concurrent searches completed in {} ms", System.currentTimeMillis() - concurrentStart);

      // 5. 基于并发结果执行依赖任务
      long dependentStart = System.currentTimeMillis();
      CompletableFuture<DependentSearchResult> dependentResult = 
          executeDependentSearches(param, searchResult);
      
      DependentSearchResult finalResult = dependentResult.get();
      log.info("Dependent searches completed in {} ms", System.currentTimeMillis() - dependentStart);

      // 6. AI 生成计划
      long aiStart = System.currentTimeMillis();
      TripRoutePlanResult tripRoutePlanResult = generateAiPlan(param, 
          finalResult.flights, finalResult.hotels, finalResult.activities);
      log.info("AI plan generation completed in {} ms", System.currentTimeMillis() - aiStart);

      long totalTime = System.currentTimeMillis() - start;
      log.info("=== PERFORMANCE SUMMARY ===");
      log.info("Total trip plan generation time: {} ms", totalTime);
      log.info("Found {} flights, {} hotels, {} activities", 
          finalResult.flights.size(), finalResult.hotels.size(), finalResult.activities.size());
      logThreadPoolStatus();
      String resultJson = JsonUtil.toJson(tripRoutePlanResult);
      log.info("Generated trip plan JSON: {}", resultJson);
      return tripRoutePlanResult;

    } catch (Exception e) {
      log.error("Failed to generate trip plan", e);
      logThreadPoolStatus();

      // 返回错误状态的计划
      TripRoutePlanResult errorPlan = new TripRoutePlanResult();
      errorPlan.setStatus(com.pkfare.trip.scale.model.enums.PlanStatus.API_ERROR.getDescription());
      errorPlan.setErrorMessage("生成旅行计划时发生错误: " + e.getMessage());
      return errorPlan;
    }
  }

  /**
   * 验证输入参数
   *
   * @param param 生成计划参数
   */
  private void validateParams(GeneratePlanParam param) {
    log.debug("Validating parameters");
    ValidationUtil.validateGeneratePlanParam(param);
  }

  /**
   * 计算是否为精确旅行时间
   *
   * @param param 生成计划参数
   * @return 是否为精确时间
   */
  private boolean calculatePreciseTravel(GeneratePlanParam param) {
    LocalDate startDate = DateUtil.parseDate(param.getStart_period());
    LocalDate endDate = DateUtil.parseDate(param.getEnd_period());
    long daysBetween = DateUtil.daysBetween(startDate, endDate) + 1;

    boolean preciseTravel = daysBetween == param.getTrip_days();
    log.debug("Precise travel calculation: daysBetween={}, tripDays={}, preciseTravel={}",
        daysBetween, param.getTrip_days(), preciseTravel);

    return preciseTravel;
  }

  /**
   * 计算是否为往返行程
   *
   * @param param 生成计划参数
   * @return 是否为往返行程
   */
  private boolean calculateRoundTrip(GeneratePlanParam param) {
    List<TripRouteParam> routes = param.getTrip_routes();
    if (routes == null || routes.isEmpty()) {
      return true; // 默认往返
    }

    String firstDestination = routes.get(0).getLocation_code();
    String lastDestination = routes.get(routes.size() - 1).getLocation_code();

    boolean roundTrip = firstDestination.equals(lastDestination);
    log.debug("Round trip calculation: first={}, last={}, roundTrip={}",
        firstDestination, lastDestination, roundTrip);

    return roundTrip;
  }

  /**
   * 并发执行独立的搜索任务
   * 这些任务之间没有依赖关系，可以并行执行
   */
  private CompletableFuture<ConcurrentSearchResult> executeConcurrentSearches(
      GeneratePlanParam param, FlightSearchResult dateResult, boolean preciseTravel, boolean roundTrip) {
    
    log.info("Starting concurrent searches with {} threads", 
        ((ThreadPoolExecutor) tripPlanExecutor).getCorePoolSize());

    // 1. 并发搜索航班
    CompletableFuture<Map<String,List<FlightInfo>>> flightsFuture = CompletableFuture
        .supplyAsync(() -> {
          long start = System.currentTimeMillis();
          try {
            Map<String,List<FlightInfo>> flights = flightSearchService.searchFlightOffers(param, dateResult, preciseTravel, roundTrip);
            log.info("[{}] Flight search completed in {} ms, found {} flights", 
                Thread.currentThread().getName(), System.currentTimeMillis() - start, flights.size());
            return flights;
          } catch (Exception e) {
            log.error("[{}] Flight search failed", Thread.currentThread().getName(), e);
            throw new RuntimeException("Flight search failed", e);
          }
        }, tripPlanExecutor);

    // 2. 并发搜索酒店ID列表
    CompletableFuture<Map<String, List<String>>> hotelIdsFuture = CompletableFuture
        .supplyAsync(() -> {
          long start = System.currentTimeMillis();
          try {
            Map<String, List<String>> hotelIds = hotelSearchService.getHotelsByCity(param.getTrip_routes());
            log.info("[{}] Hotel IDs search completed in {} ms, found {} cities", 
                Thread.currentThread().getName(), System.currentTimeMillis() - start, hotelIds.size());
            return hotelIds;
          } catch (Exception e) {
            log.error("[{}] Hotel IDs search failed", Thread.currentThread().getName(), e);
            throw new RuntimeException("Hotel IDs search failed", e);
          }
        }, tripPlanExecutor);

    // 3. 并发搜索城市位置信息
    CompletableFuture<List<CityLocationInfo>> locationsFuture = CompletableFuture
        .supplyAsync(() -> {
          long start = System.currentTimeMillis();
          try {
            List<String> cityList = param.getTrip_routes().stream()
                .map(TripRouteParam::getDestination_city)
                .collect(Collectors.toList());
            List<CityLocationInfo> locations = locationSearchService.searchCityLocations(cityList);
            log.info("[{}] City locations search completed in {} ms, found {} locations", 
                Thread.currentThread().getName(), System.currentTimeMillis() - start, locations.size());
            return locations;
          } catch (Exception e) {
            log.error("[{}] City locations search failed", Thread.currentThread().getName(), e);
            throw new RuntimeException("City locations search failed", e);
          }
        }, tripPlanExecutor);

    // 等待所有并发任务完成
    return CompletableFuture.allOf(flightsFuture, hotelIdsFuture, locationsFuture)
        .thenApply(v -> new ConcurrentSearchResult(
            flightsFuture.join(),
            hotelIdsFuture.join(),
            locationsFuture.join()
        ));
  }

  /**
   * 执行依赖于并发搜索结果的任务
   */
  private CompletableFuture<DependentSearchResult> executeDependentSearches(
      GeneratePlanParam param, ConcurrentSearchResult concurrentResult) {
    log.info("executeDependentSearches begin");
    
    // 1. 基于航班信息搜索酒店详情
    CompletableFuture<List<HotelInfo>> hotelsFuture = CompletableFuture
        .supplyAsync(() -> {
          long start = System.currentTimeMillis();
          try {
            List<HotelInfo> hotels = hotelSearchService.searchHotels(
                param, concurrentResult.flights.get("preferred"), concurrentResult.hotelIds);
            log.info("[{}] Hotel details search completed in {} ms, found {} hotels", 
                Thread.currentThread().getName(), System.currentTimeMillis() - start, hotels.size());
            return hotels;
          } catch (Exception e) {
            log.error("[{}] Hotel details search failed", Thread.currentThread().getName(), e);
            throw new RuntimeException("Hotel details search failed", e);
          }
        }, tripPlanExecutor);

    // 2. 基于位置信息搜索活动
    CompletableFuture<List<ActivityInfo>> activitiesFuture = CompletableFuture
        .supplyAsync(() -> {
          long start = System.currentTimeMillis();
          try {
            List<ActivityInfo> activities = activitySearchService.searchActivities(
                param.getTrip_routes(), concurrentResult.locations);
            log.info("[{}] Activities search completed in {} ms, found {} activities", 
                Thread.currentThread().getName(), System.currentTimeMillis() - start, activities.size());
            return activities;
          } catch (Exception e) {
            log.error("[{}] Activities search failed", Thread.currentThread().getName(), e);
            throw new RuntimeException("Activities search failed", e);
          }
        }, tripPlanExecutor);

    // 等待依赖任务完成
    return CompletableFuture.allOf(hotelsFuture, activitiesFuture)
        .thenApply(v -> new DependentSearchResult(
            concurrentResult.flights,
            hotelsFuture.join(),
            activitiesFuture.join()
        ));
  }

  /**
   * 记录线程池状态
   */
  private void logThreadPoolStatus() {
    if (tripPlanExecutor instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor executor = (ThreadPoolExecutor) tripPlanExecutor;
      log.debug("Thread Pool Status - Active: {}, Pool Size: {}, Queue Size: {}, " +
              "Completed Tasks: {}, Total Tasks: {}",
              executor.getActiveCount(),
              executor.getPoolSize(),
              executor.getQueue().size(),
              executor.getCompletedTaskCount(),
              executor.getTaskCount());
    }
  }

  /**
   * 生成AI计划
   *
   * @param param      参数
   * @param flights    航班信息
   * @param hotels     酒店信息
   * @param activities 活动信息
   * @return AI生成的计划文本
   */
  private TripRoutePlanResult generateAiPlan(GeneratePlanParam param,
      Map<String,List<FlightInfo>> flights,
      List<HotelInfo> hotels,
      List<ActivityInfo> activities) {
    try {
      SubmitAiPlanInfo planInfo = new SubmitAiPlanInfo();
      planInfo.setGeneratePlanParam(param);
      planInfo.setFlightMap(flights);
      planInfo.setHotelInfos(hotels);
      planInfo.setActivityInfos(activities);

      return googleAiService.generateAiPlan(planInfo);
    } catch (Exception e) {
      log.error("Failed to generate AI plan", e);
      TripRoutePlanResult result = new TripRoutePlanResult();
      result.setErrorMessage("生成路线图失败");
      return result;
    }
  }

  /**
   * 并发搜索结果数据结构
   */
  private static class ConcurrentSearchResult {
    final Map<String,List<FlightInfo>> flights;
    final Map<String, List<String>> hotelIds;
    final List<CityLocationInfo> locations;

    ConcurrentSearchResult(Map<String,List<FlightInfo>> flights,
                          Map<String, List<String>> hotelIds, 
                          List<CityLocationInfo> locations) {
      this.flights = flights;
      this.hotelIds = hotelIds;
      this.locations = locations;
    }
  }

  /**
   * 依赖搜索结果数据结构
   */
  private static class DependentSearchResult {
    final Map<String,List<FlightInfo>> flights;
    final List<HotelInfo> hotels;
    final List<ActivityInfo> activities;

    DependentSearchResult(Map<String,List<FlightInfo>> flights,
                         List<HotelInfo> hotels, 
                         List<ActivityInfo> activities) {
      this.flights = flights;
      this.hotels = hotels;
      this.activities = activities;
    }
  }
}
