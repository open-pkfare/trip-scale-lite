package com.pkfare.trip.scale.service.plan;

import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.ItineraryInfo;
import com.pkfare.trip.scale.plan.service.response.SegmentInfo;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringRequest;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationRequest;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationResponse;
import com.pkfare.trip.scale.service.plan.dto.DailyActivityPlan;
import com.pkfare.trip.scale.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 活动筛选服务 - 基于Google ADK大模型
 * 根据航班时间段和用户偏好对活动进行二次筛选
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class ActivityFilteringService {
    
    private final ActivityFilteringAgentManager agentManager;
    private final ExecutorService executorService;
    
    public ActivityFilteringService(ActivityFilteringAgentManager agentManager) {
        this.agentManager = agentManager;
        // 创建专用线程池用于并行AI调用
        this.executorService = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 4), // 最多4个线程
            r -> {
                Thread t = new Thread(r, "activity-filtering-ai-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );
    }
    
    private static final int MIN_ACTIVITIES_PER_DAY = 2;
    private static final int MAX_ACTIVITIES_PER_DAY = 6;
    private static final int DEFAULT_ACTIVITIES_PER_DAY = 3;
    
    /**
     * 基于航班时间和用户偏好筛选活动 - 使用按城市分批并行AI分配，返回每日活动分配
     * 
     * @param param 生成计划参数
     * @param flights 航班信息
     * @param originalActivities 原始活动列表
     * @return 每日活动分配列表
     */
    public List<DailyActivityPlan> filterActivitiesWithDailyAllocation(
            GeneratePlanParam param,
            Map<String, List<FlightInfo>> flights,
            List<ActivityInfo> originalActivities) {
        
        log.info("Starting optimized AI activity allocation with {} original activities", originalActivities.size());
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 检查Agent是否可用，如果可用则使用分批并行AI分配
            if (agentManager.isAgentAvailable() && originalActivities.size() > 5) {
                log.info("Using city-based parallel AI activity allocation for {} activities", originalActivities.size());
                
                // 2. 按城市分组活动
                Map<String, List<ActivityInfo>> activitiesByCity = groupActivitiesByCity(originalActivities, param);
                log.info("Activities grouped into {} cities: {}", activitiesByCity.size(), activitiesByCity.keySet());
                
                // 3. 并行调用AI为每个城市分配活动
                List<DailyActivityPlan> allDailyPlans = processActivitiesByCityInParallel(
                    param, flights, activitiesByCity);
                
                if (!allDailyPlans.isEmpty()) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Parallel AI allocation successful: {} daily plans generated in {} ms", 
                        allDailyPlans.size(), duration);
                    
                    return allDailyPlans;
                }
                
                log.warn("Parallel AI allocation failed or returned empty results, falling back to rule-based filtering");
            } else {
                log.debug("Using rule-based filtering (Agent unavailable or insufficient activities)");
            }
            
            // 4. Fallback到原有的逐日筛选逻辑
            return fallbackToLegacyDailyAllocation(param, flights, originalActivities);
            
        } catch (Exception e) {
            log.error("Failed to perform parallel activity allocation", e);
            // 如果并行分配失败，返回基于简单规则筛选的结果
            return fallbackToLegacyDailyAllocation(param, flights, originalActivities);
        }
    }

    /**
     * 基于航班时间和用户偏好筛选活动 - 使用全局AI分配（兼容旧接口）
     * 
     * @param param 生成计划参数
     * @param flights 航班信息
     * @param originalActivities 原始活动列表
     * @return 筛选后的活动列表
     */
    /**
    public List<ActivityInfo> filterActivitiesByFlightTimeAndPreferences(
            GeneratePlanParam param,
            Map<String, List<FlightInfo>> flights,
            List<ActivityInfo> originalActivities) {
        
        log.info("Starting global AI activity allocation with {} original activities", originalActivities.size());
        
        try {
            // 1. 检查Agent是否可用，如果可用则使用全局AI分配
            if (agentManager.isAgentAvailable() && originalActivities.size() > 5) {
                log.info("Using global AI activity allocation for {} activities", originalActivities.size());
                
                // 2. 构建全局分配请求
                GlobalActivityAllocationRequest globalRequest = buildGlobalAllocationRequest(param, flights, originalActivities);
                
                // 3. 调用全局AI分配
                GlobalActivityAllocationResponse globalResponse = agentManager.allocateActivitiesGlobally(param,globalRequest);
                
                // 4. 处理全局分配响应
                if ("SUCCESS".equals(globalResponse.getStatus()) && 
                    globalResponse.getDailyPlans() != null && 
                    !globalResponse.getDailyPlans().isEmpty()) {
                    
                    // 提取所有分配的活动
                    List<ActivityInfo> allocatedActivities = globalResponse.getDailyPlans()
                        .stream()
                        .flatMap(plan -> plan.getActivities().stream())
                        .collect(Collectors.toList());
                    
                    log.info("Global AI allocation successful: {} activities allocated across {} days", 
                        allocatedActivities.size(), globalResponse.getDailyPlans().size());
                    
                    // 记录分配理由
                    if (globalResponse.getAllocationReasoning() != null) {
                        log.debug("AI allocation reasoning: {}", globalResponse.getAllocationReasoning());
                    }
                    
                    return allocatedActivities;
                }
                
                log.warn("Global AI allocation failed or returned empty results, falling back to rule-based filtering");
            } else {
                log.debug("Using rule-based filtering (Agent unavailable or insufficient activities)");
            }
            
            // 5. Fallback到原有的逐日筛选逻辑
            return fallbackToLegacyFiltering(param, flights, originalActivities);
            
        } catch (Exception e) {
            log.error("Failed to perform global activity allocation", e);
            // 如果全局分配失败，返回基于简单规则筛选的结果
            return fallbackActivityFiltering(originalActivities, param);
        }
    }*/
    
    /**
     * Fallback到原有的逐日筛选逻辑，返回每日分配
     */
    private List<DailyActivityPlan> fallbackToLegacyDailyAllocation(
            GeneratePlanParam param, Map<String, List<FlightInfo>> flights, List<ActivityInfo> originalActivities) {
        
        log.info("Using legacy day-by-day filtering for daily allocation");
        
        try {
            // 1. 分析航班时间
            FlightTimeAnalysis flightAnalysis = analyzeFlightTimes(flights, param);
            
            // 2. 获取用户偏好
            String userPreferences = getUserPreferences("mock_user_id");
            
            // 3. 按城市和日期组织活动
            Map<String, Map<LocalDate, List<ActivityInfo>>> activitiesByCity = 
                organizeActivitiesByCity(originalActivities, param);
            
            // 4. 构建每日活动分配
            List<DailyActivityPlan> dailyPlans = new ArrayList<>();
            
            for (Map.Entry<String, Map<LocalDate, List<ActivityInfo>>> cityEntry : activitiesByCity.entrySet()) {
                String cityCode = cityEntry.getKey();
                Map<LocalDate, List<ActivityInfo>> dailyActivities = cityEntry.getValue();
                
                for (Map.Entry<LocalDate, List<ActivityInfo>> dayEntry : dailyActivities.entrySet()) {
                    LocalDate date = dayEntry.getKey();
                    List<ActivityInfo> dayActivities = dayEntry.getValue();
                    
                    // 为每一天筛选活动并构建DailyActivityPlan
                    List<ActivityInfo> filteredDayActivities = filterActivitiesForDay(
                        cityCode, date, dayActivities, flightAnalysis, userPreferences, param);
                    
                    // 构建DailyActivityPlan
                    DailyActivityPlan dailyPlan = new DailyActivityPlan();
                    dailyPlan.setDate(date);
                    dailyPlan.setCityCode(cityCode);
                    dailyPlan.setCityName(getCityNameFromRoutes(cityCode, param));
                    dailyPlan.setActivities(filteredDayActivities);
                    
                    // 设置日期类型
                    if (date.equals(flightAnalysis.getArrivalDate())) {
                        dailyPlan.setDayType("arrival_day");
                        dailyPlan.setIntensityLevel("relaxed");
                    } else if (date.equals(flightAnalysis.getDepartureDate())) {
                        dailyPlan.setDayType("departure_day");
                        dailyPlan.setIntensityLevel("relaxed");
                    } else {
                        dailyPlan.setDayType("full_day");
                        dailyPlan.setIntensityLevel(filteredDayActivities.size() > 3 ? "intensive" : "moderate");
                    }
                    
                    dailyPlans.add(dailyPlan);
                }
            }
            
            log.info("Legacy daily allocation completed. Generated {} daily plans", dailyPlans.size());
            dailyPlans.sort(Comparator.comparing(DailyActivityPlan::getDate));
            return dailyPlans;
            
        } catch (Exception e) {
            log.error("Legacy daily allocation also failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 按城市分组活动
     */
    private Map<String, List<ActivityInfo>> groupActivitiesByCity(List<ActivityInfo> originalActivities, GeneratePlanParam param) {
        Map<String, List<ActivityInfo>> activitiesByCity = new HashMap<>();
        
        // 获取行程中的所有城市
        Set<String> tripCities = param.getTrip_routes().stream()
            .map(route -> route.getLocation_code())
            .collect(Collectors.toSet());
        
        // 按城市分组活动
        for (ActivityInfo activity : originalActivities) {
            String cityCode = activity.getCityCode();
            
            // 如果活动的城市代码在行程中，则添加到对应城市组
            if (cityCode != null && tripCities.contains(cityCode)) {
                activitiesByCity.computeIfAbsent(cityCode, k -> new ArrayList<>()).add(activity);
            }
        }
        
        log.info("Activities grouped by city: {}", 
            activitiesByCity.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey, 
                    entry -> entry.getValue().size()
                )));
        
        return activitiesByCity;
    }
    
    /**
     * 并行处理各城市的活动分配
     */
    private List<DailyActivityPlan> processActivitiesByCityInParallel(
            GeneratePlanParam param, 
            Map<String, List<FlightInfo>> flights, 
            Map<String, List<ActivityInfo>> activitiesByCity) {
        
        log.info("Starting parallel processing for {} cities", activitiesByCity.size());
        
        // 创建并行任务
        List<CompletableFuture<List<DailyActivityPlan>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<ActivityInfo>> cityEntry : activitiesByCity.entrySet()) {
            String cityCode = cityEntry.getKey();
            List<ActivityInfo> cityActivities = cityEntry.getValue();
            
            // 为每个城市创建异步任务
            CompletableFuture<List<DailyActivityPlan>> future = CompletableFuture
                .supplyAsync(() -> processCityActivitiesWithAI(param, flights, cityCode, cityActivities), executorService)
                .exceptionally(throwable -> {
                    log.error("Failed to process activities for city {}: {}", cityCode, throwable.getMessage());
                    // 如果AI处理失败，使用规则引擎作为fallback
                    return processCityActivitiesWithRules(param, flights, cityCode, cityActivities);
                });
            
            futures.add(future);
        }
        
        // 等待所有任务完成并合并结果
        List<DailyActivityPlan> allDailyPlans = new ArrayList<>();
        
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(); // 等待所有任务完成
            
            // 收集所有结果
            for (CompletableFuture<List<DailyActivityPlan>> future : futures) {
                List<DailyActivityPlan> cityPlans = future.get();
                if (cityPlans != null && !cityPlans.isEmpty()) {
                    allDailyPlans.addAll(cityPlans);
                }
            }
            
            // 按日期排序
            allDailyPlans.sort(Comparator.comparing(DailyActivityPlan::getDate));
            
            log.info("Parallel processing completed. Total daily plans: {}", allDailyPlans.size());
            
        } catch (Exception e) {
            log.error("Failed to complete parallel processing", e);
            return new ArrayList<>();
        }
        
        return allDailyPlans;
    }
    
    /**
     * 使用AI处理单个城市的活动分配
     */
    private List<DailyActivityPlan> processCityActivitiesWithAI(
            GeneratePlanParam param, 
            Map<String, List<FlightInfo>> flights, 
            String cityCode, 
            List<ActivityInfo> cityActivities) {
        
        log.info("Processing {} activities for city {} with AI", cityActivities.size(), cityCode);
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建城市特定的全局分配请求
            GlobalActivityAllocationRequest cityRequest = buildCitySpecificAllocationRequest(
                param, flights, cityCode, cityActivities);
            
            // 调用AI进行分配
            GlobalActivityAllocationResponse response = agentManager.allocateActivitiesGlobally(param, cityRequest);
            
            if ("SUCCESS".equals(response.getStatus()) && 
                response.getDailyPlans() != null && 
                !response.getDailyPlans().isEmpty()) {
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("AI processing completed for city {} in {} ms: {} daily plans", 
                    cityCode, duration, response.getDailyPlans().size());
                
                return response.getDailyPlans();
            } else {
                log.warn("AI processing failed for city {}, using rule-based fallback", cityCode);
                return processCityActivitiesWithRules(param, flights, cityCode, cityActivities);
            }
            
        } catch (Exception e) {
            log.error("AI processing failed for city {}: {}", cityCode, e);
            return processCityActivitiesWithRules(param, flights, cityCode, cityActivities);
        }
    }
    
    /**
     * 使用规则引擎处理单个城市的活动分配（作为AI的fallback）
     */
    private List<DailyActivityPlan> processCityActivitiesWithRules(
            GeneratePlanParam param, 
            Map<String, List<FlightInfo>> flights, 
            String cityCode, 
            List<ActivityInfo> cityActivities) {
        
        log.info("Processing {} activities for city {} with rules", cityActivities.size(), cityCode);
        
        try {
            // 获取该城市的停留信息
            var cityRoute = param.getTrip_routes().stream()
                .filter(route -> cityCode.equals(route.getLocation_code()))
                .findFirst()
                .orElse(null);
            
            if (cityRoute == null) {
                log.warn("No route information found for city {}", cityCode);
                return new ArrayList<>();
            }
            
            // 分析航班时间
            FlightTimeAnalysis flightAnalysis = analyzeFlightTimes(flights, param);
            
            // 获取用户偏好
            String userPreferences = getUserPreferences("mock_user_id");
            
            // 计算该城市的起始日期
            LocalDate cityStartDate = calculateCityStartDate(param, cityCode);
            
            // 为该城市的每一天分配活动
            List<DailyActivityPlan> cityPlans = new ArrayList<>();
            
            for (int day = 0; day < cityRoute.getStay_days(); day++) {
                LocalDate date = cityStartDate.plusDays(day);
                
                // 为这一天筛选活动
                List<ActivityInfo> dayActivities = filterActivitiesForDay(
                    cityCode, date, cityActivities, flightAnalysis, userPreferences, param);
                
                // 构建DailyActivityPlan
                DailyActivityPlan dailyPlan = new DailyActivityPlan();
                dailyPlan.setDate(date);
                dailyPlan.setCityCode(cityCode);
                dailyPlan.setCityName(cityRoute.getDestination_city());
                dailyPlan.setActivities(dayActivities);
                
                // 设置日期类型
                if (date.equals(flightAnalysis.getArrivalDate())) {
                    dailyPlan.setDayType("arrival_day");
                    dailyPlan.setIntensityLevel("relaxed");
                } else if (date.equals(flightAnalysis.getDepartureDate())) {
                    dailyPlan.setDayType("departure_day");
                    dailyPlan.setIntensityLevel("relaxed");
                } else {
                    dailyPlan.setDayType("full_day");
                    dailyPlan.setIntensityLevel(dayActivities.size() > 3 ? "intensive" : "moderate");
                }
                
                cityPlans.add(dailyPlan);
            }
            
            log.info("Rule-based processing completed for city {}: {} daily plans", cityCode, cityPlans.size());
            return cityPlans;
            
        } catch (Exception e) {
            log.error("Rule-based processing failed for city {}: {}", cityCode, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建城市特定的全局分配请求
     */
    private GlobalActivityAllocationRequest buildCitySpecificAllocationRequest(
            GeneratePlanParam param, 
            Map<String, List<FlightInfo>> flights, 
            String cityCode, 
            List<ActivityInfo> cityActivities) {
        
        // 获取该城市的路线信息
        TripRouteParam cityRoute = param.getTrip_routes().stream()
            .filter(route -> cityCode.equals(route.getLocation_code()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No route found for city: " + cityCode));
        
        GlobalActivityAllocationRequest request = new GlobalActivityAllocationRequest();
        request.setAllActivities(cityActivities);
        request.setBudget(param.getBudgets());
        request.setCurrency(param.getCurrency());
        
        // 构建该城市的行程信息
        GlobalActivityAllocationRequest.TripItinerary itinerary = new GlobalActivityAllocationRequest.TripItinerary();
        LocalDate cityStartDate = calculateCityStartDate(param, cityCode);
        itinerary.setStartDate(cityStartDate);
        itinerary.setEndDate(cityStartDate.plusDays(cityRoute.getStay_days() - 1));
        itinerary.setTotalDays(cityRoute.getStay_days());
        
        // 构建单个城市停留信息
        List<GlobalActivityAllocationRequest.CityStay> cityStays = new ArrayList<>();
        GlobalActivityAllocationRequest.CityStay cityStay = new GlobalActivityAllocationRequest.CityStay();
        cityStay.setCityCode(cityCode);
        cityStay.setCityName(cityRoute.getDestination_city());
        cityStay.setStartDate(cityStartDate);
        cityStay.setEndDate(cityStartDate.plusDays(cityRoute.getStay_days() - 1));
        cityStay.setStayDays(cityRoute.getStay_days());
        cityStay.setReasonForRecommendation(cityRoute.getReason_for_recommendation());
        cityStays.add(cityStay);
        
        itinerary.setCityStays(cityStays);
        request.setItinerary(itinerary);
        
        // 构建航班约束（只包含影响该城市的航班）
        FlightTimeAnalysis flightAnalysis = analyzeFlightTimes(flights, param);
        GlobalActivityAllocationRequest.FlightConstraints flightConstraints = 
            new GlobalActivityAllocationRequest.FlightConstraints();
        
        // 只有当该城市包含到达日或离开日时才设置航班约束
        if (flightAnalysis.getArrivalDate() != null && 
            !flightAnalysis.getArrivalDate().isBefore(cityStartDate) && 
            !flightAnalysis.getArrivalDate().isAfter(cityStartDate.plusDays(cityRoute.getStay_days() - 1))) {
            flightConstraints.setArrivalDate(flightAnalysis.getArrivalDate());
            flightConstraints.setArrivalTime(flightAnalysis.getArrivalTime() != null ? 
                flightAnalysis.getArrivalTime().toString() : null);
        }
        
        if (flightAnalysis.getDepartureDate() != null && 
            !flightAnalysis.getDepartureDate().isBefore(cityStartDate) && 
            !flightAnalysis.getDepartureDate().isAfter(cityStartDate.plusDays(cityRoute.getStay_days() - 1))) {
            flightConstraints.setDepartureDate(flightAnalysis.getDepartureDate());
            flightConstraints.setDepartureTime(flightAnalysis.getDepartureTime() != null ? 
                flightAnalysis.getDepartureTime().toString() : null);
        }
        
        // 构建该城市的每日类型映射
        Map<LocalDate, String> dayTypes = new HashMap<>();
        LocalDate date = cityStartDate;
        for (int day = 0; day < cityRoute.getStay_days(); day++) {
            LocalDate currentDate = date.plusDays(day);
            if (currentDate.equals(flightAnalysis.getArrivalDate())) {
                dayTypes.put(currentDate, "arrival_day");
            } else if (currentDate.equals(flightAnalysis.getDepartureDate())) {
                dayTypes.put(currentDate, "departure_day");
            } else {
                dayTypes.put(currentDate, "full_day");
            }
        }
        flightConstraints.setDayTypes(dayTypes);
        request.setFlightConstraints(flightConstraints);
        
        // 构建用户偏好
        try {
            String userPreferencesJson = getUserPreferences("mock_user_id");
            Map<String, Object> preferences = JsonUtil.fromJson(userPreferencesJson, Map.class);
            ActivityFilteringRequest.UserPreferences userPref = new ActivityFilteringRequest.UserPreferences();
            userPref.setLikes((List<String>) preferences.getOrDefault("likes", new ArrayList<>()));
            userPref.setHates((List<String>) preferences.getOrDefault("hates", new ArrayList<>()));
            userPref.setPrefer((List<String>) preferences.getOrDefault("prefer", new ArrayList<>()));
            request.setUserPreferences(userPref);
        } catch (Exception e) {
            log.warn("Failed to parse user preferences for city {}, using default", cityCode, e);
            ActivityFilteringRequest.UserPreferences defaultPref = new ActivityFilteringRequest.UserPreferences();
            defaultPref.setLikes(new ArrayList<>());
            defaultPref.setHates(new ArrayList<>());
            defaultPref.setPrefer(new ArrayList<>());
            request.setUserPreferences(defaultPref);
        }
        
        return request;
    }
    
    /**
     * 计算城市的起始日期
     */
    private LocalDate calculateCityStartDate(GeneratePlanParam param, String targetCityCode) {
        LocalDate currentDate = LocalDate.parse(param.getStart_period());
        
        for (var routeParam : param.getTrip_routes()) {
            if (targetCityCode.equals(routeParam.getLocation_code())) {
                return currentDate;
            }
            currentDate = currentDate.plusDays(routeParam.getStay_days());
        }
        
        // 如果没找到，返回行程开始日期
        return LocalDate.parse(param.getStart_period());
    }
    
    /**
     * Fallback到原有的逐日筛选逻辑
     */
    /**
    private List<ActivityInfo> fallbackToLegacyFiltering(
            GeneratePlanParam param, Map<String, List<FlightInfo>> flights, List<ActivityInfo> originalActivities) {
        
        log.info("Using legacy day-by-day filtering as fallback");
        
        try {
            // 1. 分析航班时间
            FlightTimeAnalysis flightAnalysis = analyzeFlightTimes(flights, param);
            
            // 2. 获取用户偏好
            String userPreferences = getUserPreferences("mock_user_id");
            
            // 3. 按城市和日期组织活动
            Map<String, Map<LocalDate, List<ActivityInfo>>> activitiesByCity = 
                organizeActivitiesByCity(originalActivities, param);
            
            // 4. 使用逐日筛选
            List<ActivityInfo> filteredActivities = new ArrayList<>();
            
            for (Map.Entry<String, Map<LocalDate, List<ActivityInfo>>> cityEntry : activitiesByCity.entrySet()) {
                String cityCode = cityEntry.getKey();
                Map<LocalDate, List<ActivityInfo>> dailyActivities = cityEntry.getValue();
                
                for (Map.Entry<LocalDate, List<ActivityInfo>> dayEntry : dailyActivities.entrySet()) {
                    LocalDate date = dayEntry.getKey();
                    List<ActivityInfo> dayActivities = dayEntry.getValue();
                    
                    // 为每一天筛选活动
                    List<ActivityInfo> filteredDayActivities = filterActivitiesForDay(
                        cityCode, date, dayActivities, flightAnalysis, userPreferences, param);
                    
                    filteredActivities.addAll(filteredDayActivities);
                }
            }
            
            log.info("Legacy filtering completed. Filtered from {} to {} activities", 
                originalActivities.size(), filteredActivities.size());
            
            return filteredActivities;
            
        } catch (Exception e) {
            log.error("Legacy filtering also failed", e);
            return fallbackActivityFiltering(originalActivities, param);
        }
    }
    */
    /**
     * 分析航班时间
     */
    private FlightTimeAnalysis analyzeFlightTimes(Map<String, List<FlightInfo>> flights, GeneratePlanParam param) {
        FlightTimeAnalysis analysis = new FlightTimeAnalysis();
        
        List<FlightInfo> preferredFlights = flights.getOrDefault("preferred", new ArrayList<>());
        
        if (!preferredFlights.isEmpty()) {
            // 分析去程航班（第一个航班）
            FlightInfo outboundFlight = preferredFlights.get(0);
            if (outboundFlight.getItineraries() != null && !outboundFlight.getItineraries().isEmpty()) {
                ItineraryInfo firstItinerary = outboundFlight.getItineraries().get(0);
                if (firstItinerary.getSegments() != null && !firstItinerary.getSegments().isEmpty()) {
                    SegmentInfo lastSegment = firstItinerary.getSegments().get(firstItinerary.getSegments().size() - 1);
                    if (lastSegment.getArrivalTime() != null) {
                        analysis.setArrivalTime(parseTime(lastSegment.getArrivalTime()));
                        analysis.setArrivalDate(parseDate(lastSegment.getArrivalTime()));
                    }
                }
            }
            
            // 分析返程航班（最后一个航班）
            if (preferredFlights.size() > 1) {
                FlightInfo returnFlight = preferredFlights.get(preferredFlights.size() - 1);
                if (returnFlight.getItineraries() != null && !returnFlight.getItineraries().isEmpty()) {
                    ItineraryInfo lastItinerary = returnFlight.getItineraries().get(returnFlight.getItineraries().size() - 1);
                    if (lastItinerary.getSegments() != null && !lastItinerary.getSegments().isEmpty()) {
                        SegmentInfo firstSegment = lastItinerary.getSegments().get(0);
                        if (firstSegment.getDepartureTime() != null) {
                            analysis.setDepartureTime(parseTime(firstSegment.getDepartureTime()));
                            analysis.setDepartureDate(parseDate(firstSegment.getDepartureTime()));
                        }
                    }
                }
            }
        }
        
        return analysis;
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Shutting down activity filtering executor service");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 获取用户偏好
     */
    private String getUserPreferences(String userId) {
        try {
            // 这里应该调用PersonalPreferenceService，但为了简化，直接返回mock数据
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("likes", Arrays.asList("ancient building", "history", "art", "local food", "city walk", "religious story"));
            preferences.put("hates", Arrays.asList("modern building", "crowded place", "fast food", "noisy environment"));
            preferences.put("prefer", Arrays.asList("lone ranger", "adventure", "price-sensitive"));
            
            return JsonUtil.toJson(preferences);
        } catch (Exception e) {
            log.warn("Failed to get user preferences, using default", e);
            return "{}";
        }
    }
    
    /**
     * 按城市和日期组织活动 - 修复版本：确保每个活动在整个行程中只使用一次，避免重复
     */
    private Map<String, Map<LocalDate, List<ActivityInfo>>> organizeActivitiesByCity(
            List<ActivityInfo> activities, GeneratePlanParam param) {
        
        Map<String, Map<LocalDate, List<ActivityInfo>>> result = new HashMap<>();
        String departure = param.getStart_period();
        LocalDate currentDate = LocalDate.parse(departure);
        
        Random random = new Random();
        
        // 创建活动池的副本，用于逐步消耗（避免修改原列表）
        List<ActivityInfo> availableActivities = new ArrayList<>(activities);
        Set<String> usedActivityIds = new HashSet<>(); // 跟踪已使用的活动ID
        
        log.info("Starting activity organization with {} total activities", availableActivities.size());
        
        // 为每个城市按日期分配活动
        for (var routeParam : param.getTrip_routes()) {
            String cityCode = routeParam.getLocation_code();
            Map<LocalDate, List<ActivityInfo>> dailyActivities = new HashMap<>();
            int totalDays = routeParam.getStay_days();
            
            // 过滤出该城市的活动
            List<ActivityInfo> citySpecificActivities = availableActivities.stream()
                .filter(activity -> activity.getCityCode() != null && 
                                  activity.getCityCode().equals(cityCode) &&
                                  activity.getActivityId() != null &&
                                  !usedActivityIds.contains(activity.getActivityId()))
                .collect(Collectors.toList());
            
            log.info("City {} has {} available activities for {} days", 
                cityCode, citySpecificActivities.size(), totalDays);
            
            // 如果该城市没有足够的专属活动，使用通用活动池
            if (citySpecificActivities.size() < totalDays * 2) { // 至少每天2个活动
                List<ActivityInfo> generalActivities = availableActivities.stream()
                    .filter(activity -> activity.getActivityId() != null &&
                                      !usedActivityIds.contains(activity.getActivityId()))
                    .collect(Collectors.toList());
                
                // 将通用活动添加到城市活动池中（避免重复添加）
                for (ActivityInfo generalActivity : generalActivities) {
                    if (!citySpecificActivities.contains(generalActivity)) {
                        citySpecificActivities.add(generalActivity);
                    }
                }
                
                log.debug("Extended city {} activities to {} total activities", 
                    cityCode, citySpecificActivities.size());
            }
            
            // 为该城市的每一天分配活动
            for (int day = 0; day < totalDays; day++) {
                LocalDate date = currentDate.plusDays(day);
                List<ActivityInfo> dayActivities = new ArrayList<>();
                
                if (!citySpecificActivities.isEmpty()) {
                    // 根据可用活动数量和天数计算每天的活动数量
                    int remainingDays = totalDays - day;
                    int remainingActivities = (int) citySpecificActivities.stream()
                        .filter(activity -> !usedActivityIds.contains(activity.getActivityId()))
                        .count();
                    
                    // 计算目标活动数量（3-6个，但要考虑剩余活动数量）
                    int baseTargetCount = 3 + random.nextInt(4); // 3到6个活动
                    int maxPossibleCount = Math.max(1, remainingActivities / remainingDays);
                    int targetCount = Math.min(baseTargetCount, maxPossibleCount);
                    targetCount = Math.max(targetCount, 1); // 至少1个活动
                    
                    log.debug("Day {} in city {}: targeting {} activities (remaining: {}, days left: {})", 
                        date, cityCode, targetCount, remainingActivities, remainingDays);
                    
                    // 从城市活动池中选择未使用的活动
                    List<ActivityInfo> candidateActivities = citySpecificActivities.stream()
                        .filter(activity -> !usedActivityIds.contains(activity.getActivityId()))
                        .collect(Collectors.toList());
                    
                    // 随机选择活动（确保不重复）
                    Collections.shuffle(candidateActivities, random);
                    
                    for (int i = 0; i < Math.min(targetCount, candidateActivities.size()); i++) {
                        ActivityInfo selectedActivity = candidateActivities.get(i);
                        dayActivities.add(selectedActivity);
                        usedActivityIds.add(selectedActivity.getActivityId());
                    }
                }
                
                dailyActivities.put(date, dayActivities);
                log.debug("Day {}: {} unique activities assigned for city {} (total used: {})", 
                    date, dayActivities.size(), cityCode, usedActivityIds.size());
            }
            
            result.put(cityCode, dailyActivities);
            currentDate = currentDate.plusDays(totalDays);
        }
        
        // 最终验证：检查是否有重复活动
        Set<String> finalUsedIds = new HashSet<>();
        int duplicateCount = 0;
        int totalAssignedActivities = 0;
        
        for (Map.Entry<String, Map<LocalDate, List<ActivityInfo>>> cityEntry : result.entrySet()) {
            String cityCode = cityEntry.getKey();
            for (Map.Entry<LocalDate, List<ActivityInfo>> dayEntry : cityEntry.getValue().entrySet()) {
                LocalDate date = dayEntry.getKey();
                for (ActivityInfo activity : dayEntry.getValue()) {
                    totalAssignedActivities++;
                    if (activity.getActivityId() != null) {
                        if (finalUsedIds.contains(activity.getActivityId())) {
                            duplicateCount++;
                            log.warn("DUPLICATE DETECTED: Activity {} on {} in city {}", 
                                activity.getActivityId(), date, cityCode);
                        } else {
                            finalUsedIds.add(activity.getActivityId());
                        }
                    }
                }
            }
        }
        
        log.info("Activity organization completed: {} cities, {} total activities assigned, {} unique, {} duplicates", 
            result.size(), totalAssignedActivities, finalUsedIds.size(), duplicateCount);
        
        return result;
    }
    
    /**
     * 为特定日期筛选活动
     */
    private List<ActivityInfo> filterActivitiesForDay(
            String cityCode, LocalDate date, List<ActivityInfo> dayActivities,
            FlightTimeAnalysis flightAnalysis, String userPreferences, GeneratePlanParam param) {
        
        try {
            // Fallback到基于规则的筛选
            return filterActivitiesByRulesWithPreferences(dayActivities, date, flightAnalysis, userPreferences);
        } catch (Exception e) {
            log.error("Failed to use AI filtering for day {}, falling back to rule-based filtering", date, e);
            return filterActivitiesByRules(dayActivities, date, flightAnalysis);
        }
    }
    
    /**
     * 构建Agent请求
     */
    private ActivityFilteringRequest buildAgentRequest(
            String cityCode, LocalDate date, List<ActivityInfo> dayActivities,
            FlightTimeAnalysis flightAnalysis, String userPreferences, GeneratePlanParam param) {
        
        ActivityFilteringRequest request = new ActivityFilteringRequest();
        request.setCityCode(cityCode);
        request.setCityName(getCityNameFromRoutes(cityCode, param));
        request.setDate(date);
        request.setCandidateActivities(dayActivities);
        request.setBudget(param.getBudgets());
        request.setCurrency(param.getCurrency());
        
        // 构建航班时间信息
        ActivityFilteringRequest.FlightTimeInfo flightInfo = new ActivityFilteringRequest.FlightTimeInfo();
        flightInfo.setDate(date);
        
        if (flightAnalysis.getArrivalDate() != null && date.equals(flightAnalysis.getArrivalDate())) {
            flightInfo.setType("arrival_day");
            flightInfo.setArrivalTime(flightAnalysis.getArrivalTime() != null ? 
                flightAnalysis.getArrivalTime().toString() : null);
        } else if (flightAnalysis.getDepartureDate() != null && date.equals(flightAnalysis.getDepartureDate())) {
            flightInfo.setType("departure_day");
            flightInfo.setDepartureTime(flightAnalysis.getDepartureTime() != null ? 
                flightAnalysis.getDepartureTime().toString() : null);
        } else {
            flightInfo.setType("full_day");
        }
        
        request.setFlightInfo(flightInfo);
        
        // 构建用户偏好信息
        try {
            Map<String, Object> preferences = JsonUtil.fromJson(userPreferences, Map.class);
            ActivityFilteringRequest.UserPreferences userPref = new ActivityFilteringRequest.UserPreferences();
            userPref.setLikes((List<String>) preferences.getOrDefault("likes", new ArrayList<>()));
            userPref.setHates((List<String>) preferences.getOrDefault("hates", new ArrayList<>()));
            userPref.setPrefer((List<String>) preferences.getOrDefault("prefer", new ArrayList<>()));
            request.setUserPreferences(userPref);
        } catch (Exception e) {
            log.warn("Failed to parse user preferences, using default", e);
            ActivityFilteringRequest.UserPreferences defaultPref = new ActivityFilteringRequest.UserPreferences();
            defaultPref.setLikes(new ArrayList<>());
            defaultPref.setHates(new ArrayList<>());
            defaultPref.setPrefer(new ArrayList<>());
            request.setUserPreferences(defaultPref);
        }
        
        return request;
    }
    
    /**
     * 从路线参数中获取城市名称
     */
    private String getCityNameFromRoutes(String cityCode, GeneratePlanParam param) {
        return param.getTrip_routes().stream()
            .filter(route -> cityCode.equals(route.getLocation_code()))
            .map(route -> route.getDestination_city())
            .findFirst()
            .orElse(cityCode);
    }
    
    /**
     * 基于规则和用户偏好的活动筛选
     */
    private List<ActivityInfo> filterActivitiesByRulesWithPreferences(
            List<ActivityInfo> activities, LocalDate date, FlightTimeAnalysis flightAnalysis, String userPreferences) {
        
        // 首先基于航班时间筛选
        List<ActivityInfo> timeFilteredActivities = filterActivitiesByRules(activities, date, flightAnalysis);
        
        // 然后基于用户偏好进一步筛选
        try {
            // 解析用户偏好
            Map<String, Object> preferences = JsonUtil.fromJson(userPreferences, Map.class);
            List<String> likes = (List<String>) preferences.getOrDefault("likes", new ArrayList<>());
            List<String> hates = (List<String>) preferences.getOrDefault("hates", new ArrayList<>());
            
            // 基于偏好评分活动
            return timeFilteredActivities.stream()
                .map(activity -> {
                    double preferenceScore = calculatePreferenceScore(activity, likes, hates);
                    // 将偏好评分与原始评分结合
                    double combinedScore = activity.getRating() * 0.7 + preferenceScore * 0.3;
                    activity.setRating(combinedScore);
                    return activity;
                })
                .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Failed to apply user preferences, using time-based filtering only", e);
            return timeFilteredActivities;
        }
    }
    
    /**
     * 计算活动的偏好评分
     */
    private double calculatePreferenceScore(ActivityInfo activity, List<String> likes, List<String> hates) {
        double score = 3.0; // 基础分数
        
        String activityName = activity.getName().toLowerCase();
        String activityDesc = activity.getDescription() != null ? activity.getDescription().toLowerCase() : "";
        
        // 检查喜好匹配
        for (String like : likes) {
            if (activityName.contains(like.toLowerCase()) || activityDesc.contains(like.toLowerCase())) {
                score += 1.0;
            }
        }
        
        // 检查不喜欢的内容
        for (String hate : hates) {
            if (activityName.contains(hate.toLowerCase()) || activityDesc.contains(hate.toLowerCase())) {
                score -= 1.0;
            }
        }
        
        return Math.max(0.0, Math.min(5.0, score)); // 限制在0-5分之间
    }
    
    /**
     * 基于规则的活动筛选（作为AI筛选的fallback）
     */
    private List<ActivityInfo> filterActivitiesByRules(
            List<ActivityInfo> activities, LocalDate date, FlightTimeAnalysis flightAnalysis) {
        
        // 根据日期类型确定活动数量
        int targetCount = 2 + new Random().nextInt(4); // 2到5随机取一个目标数
        
        // 到达日：减少活动数量
        if (flightAnalysis.getArrivalDate() != null && date.equals(flightAnalysis.getArrivalDate())) {
            if (flightAnalysis.getArrivalTime() != null /**&& flightAnalysis.getArrivalTime().isAfter(LocalTime.of(15, 0))*/) {
                targetCount = MIN_ACTIVITIES_PER_DAY; // 下午到达，只安排2个活动
            }
        }
        
        // 离开日：减少活动数量
        if (flightAnalysis.getDepartureDate() != null && date.equals(flightAnalysis.getDepartureDate())) {
            if (flightAnalysis.getDepartureTime() != null /** && flightAnalysis.getDepartureTime().isBefore(LocalTime.of(15, 0))*/) {
                targetCount = MIN_ACTIVITIES_PER_DAY; // 下午前离开，只安排2个活动
            }
        }
        
        // 按评分排序并选择前N个
        return activities.stream()
            .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
            .limit(targetCount)
            .collect(Collectors.toList());
    }
    
    /**
     * 构建特定日期的航班信息
     */
    private String buildFlightInfoForDay(LocalDate date, FlightTimeAnalysis analysis) {
        Map<String, Object> flightInfo = new HashMap<>();
        
        if (analysis.getArrivalDate() != null && date.equals(analysis.getArrivalDate())) {
            flightInfo.put("type", "arrival_day");
            flightInfo.put("arrival_time", analysis.getArrivalTime() != null ? 
                analysis.getArrivalTime().toString() : "unknown");
        } else if (analysis.getDepartureDate() != null && date.equals(analysis.getDepartureDate())) {
            flightInfo.put("type", "departure_day");
            flightInfo.put("departure_time", analysis.getDepartureTime() != null ? 
                analysis.getDepartureTime().toString() : "unknown");
        } else {
            flightInfo.put("type", "full_day");
        }
        
        flightInfo.put("date", date.toString());
        
        try {
            return JsonUtil.toJson(flightInfo);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * 构建城市信息
     */
    private String buildCityInfo(String cityCode, LocalDate date, GeneratePlanParam param) {
        Map<String, Object> cityInfo = new HashMap<>();
        cityInfo.put("city_code", cityCode);
        cityInfo.put("date", date.toString());
        cityInfo.put("budget", param.getBudgets());
        cityInfo.put("currency", param.getCurrency());
        
        try {
            return JsonUtil.toJson(cityInfo);
        } catch (Exception e) {
            return "{}";
        }
    }

    
    /**
     * 解析时间字符串
     */
    private LocalTime parseTime(String timeStr) {
        try {
            if (timeStr != null && !timeStr.isEmpty()) {
                // 支持多种时间格式
                if (timeStr.contains("T")) {
                    return LocalTime.parse(timeStr.split("T")[1].substring(0, 5));
                } else if (timeStr.contains(":")) {
                    return LocalTime.parse(timeStr.substring(0, 5));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr, e);
        }
        return null;
    }
    
    /**
     * 解析日期字符串
     */
    private LocalDate parseDate(String dateStr) {
        try {
            if (dateStr != null && !dateStr.isEmpty()) {
                if (dateStr.contains("T")) {
                    return LocalDate.parse(dateStr.split("T")[0]);
                } else {
                    return LocalDate.parse(dateStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr, e);
        }
        return null;
    }
    
    /**
     * 航班时间分析结果
     */
    private static class FlightTimeAnalysis {
        private LocalTime arrivalTime;
        private LocalDate arrivalDate;
        private LocalTime departureTime;
        private LocalDate departureDate;
        
        // Getters and Setters
        public LocalTime getArrivalTime() { return arrivalTime; }
        public void setArrivalTime(LocalTime arrivalTime) { this.arrivalTime = arrivalTime; }
        
        public LocalDate getArrivalDate() { return arrivalDate; }
        public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
        
        public LocalTime getDepartureTime() { return departureTime; }
        public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
        
        public LocalDate getDepartureDate() { return departureDate; }
        public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
    }
}
