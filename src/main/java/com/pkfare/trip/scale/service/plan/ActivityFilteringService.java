package com.pkfare.trip.scale.service.plan;

import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.ItineraryInfo;
import com.pkfare.trip.scale.plan.service.response.SegmentInfo;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringRequest;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringResponse;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationRequest;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationResponse;
import com.pkfare.trip.scale.service.plan.dto.DailyActivityPlan;
import com.pkfare.trip.scale.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
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
    
    public ActivityFilteringService(ActivityFilteringAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    private static final int MIN_ACTIVITIES_PER_DAY = 2;
    private static final int MAX_ACTIVITIES_PER_DAY = 6;
    private static final int DEFAULT_ACTIVITIES_PER_DAY = 3;
    
    /**
     * 基于航班时间和用户偏好筛选活动 - 使用全局AI分配，返回每日活动分配
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
                    
                    log.info("Global AI allocation successful: {} daily plans generated", 
                        globalResponse.getDailyPlans().size());
                    
                    // 记录分配理由
                    if (globalResponse.getAllocationReasoning() != null) {
                        log.debug("AI allocation reasoning: {}", globalResponse.getAllocationReasoning());
                    }
                    
                    return globalResponse.getDailyPlans();
                }
                
                log.warn("Global AI allocation failed or returned empty results, falling back to rule-based filtering");
            } else {
                log.debug("Using rule-based filtering (Agent unavailable or insufficient activities)");
            }
            
            // 5. Fallback到原有的逐日筛选逻辑
            return fallbackToLegacyDailyAllocation(param, flights, originalActivities);
            
        } catch (Exception e) {
            log.error("Failed to perform global activity allocation", e);
            // 如果全局分配失败，返回基于简单规则筛选的结果
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
            
            return dailyPlans;
            
        } catch (Exception e) {
            log.error("Legacy daily allocation also failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建全局活动分配请求
     */
    public GlobalActivityAllocationRequest buildGlobalAllocationRequest(
            GeneratePlanParam param, Map<String, List<FlightInfo>> flights, List<ActivityInfo> originalActivities) {
        
        GlobalActivityAllocationRequest request = new GlobalActivityAllocationRequest();
        request.setAllActivities(originalActivities);
        request.setBudget(param.getBudgets());
        request.setCurrency(param.getCurrency());
        
        // 构建行程信息
        GlobalActivityAllocationRequest.TripItinerary itinerary = new GlobalActivityAllocationRequest.TripItinerary();
        itinerary.setStartDate(LocalDate.parse(param.getStart_period()));
        itinerary.setEndDate(LocalDate.parse(param.getEnd_period()));
        itinerary.setTotalDays(param.getTrip_days());
        
        // 构建城市停留信息
        List<GlobalActivityAllocationRequest.CityStay> cityStays = new ArrayList<>();
        LocalDate currentDate = itinerary.getStartDate();
        
        for (var routeParam : param.getTrip_routes()) {
            GlobalActivityAllocationRequest.CityStay cityStay = new GlobalActivityAllocationRequest.CityStay();
            cityStay.setCityCode(routeParam.getLocation_code());
            cityStay.setCityName(routeParam.getDestination_city());
            cityStay.setStartDate(currentDate);
            cityStay.setEndDate(currentDate.plusDays(routeParam.getStay_days() - 1));
            cityStay.setStayDays(routeParam.getStay_days());
            cityStay.setReasonForRecommendation(routeParam.getReason_for_recommendation());
            
            cityStays.add(cityStay);
            currentDate = currentDate.plusDays(routeParam.getStay_days());
        }
        
        itinerary.setCityStays(cityStays);
        request.setItinerary(itinerary);
        
        // 构建航班约束
        FlightTimeAnalysis flightAnalysis = analyzeFlightTimes(flights, param);
        GlobalActivityAllocationRequest.FlightConstraints flightConstraints = 
            new GlobalActivityAllocationRequest.FlightConstraints();
        flightConstraints.setArrivalDate(flightAnalysis.getArrivalDate());
        flightConstraints.setArrivalTime(flightAnalysis.getArrivalTime() != null ? 
            flightAnalysis.getArrivalTime().toString() : null);
        flightConstraints.setDepartureDate(flightAnalysis.getDepartureDate());
        flightConstraints.setDepartureTime(flightAnalysis.getDepartureTime() != null ? 
            flightAnalysis.getDepartureTime().toString() : null);
        
        // 构建每日类型映射
        Map<LocalDate, String> dayTypes = new HashMap<>();
        LocalDate date = itinerary.getStartDate();
        while (!date.isAfter(itinerary.getEndDate())) {
            if (date.equals(flightAnalysis.getArrivalDate())) {
                dayTypes.put(date, "arrival_day");
            } else if (date.equals(flightAnalysis.getDepartureDate())) {
                dayTypes.put(date, "departure_day");
            } else {
                dayTypes.put(date, "full_day");
            }
            date = date.plusDays(1);
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
            log.warn("Failed to parse user preferences for global allocation, using default", e);
            ActivityFilteringRequest.UserPreferences defaultPref = new ActivityFilteringRequest.UserPreferences();
            defaultPref.setLikes(new ArrayList<>());
            defaultPref.setHates(new ArrayList<>());
            defaultPref.setPrefer(new ArrayList<>());
            request.setUserPreferences(defaultPref);
        }
        
        return request;
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
     * 按城市和日期组织活动 - 超简化版本：直接从activities列表中依次为每个城市每天取3～6个活动
     */
    private Map<String, Map<LocalDate, List<ActivityInfo>>> organizeActivitiesByCity(
            List<ActivityInfo> activities, GeneratePlanParam param) {
        
        Map<String, Map<LocalDate, List<ActivityInfo>>> result = new HashMap<>();
        String departure = param.getStart_period();
        LocalDate currentDate = LocalDate.parse(departure);
        
        Random random = new Random();
        int activityIndex = 0; // 用于依次取活动的索引
        
        // 为每个城市按日期分配活动
        for (var routeParam : param.getTrip_routes()) {
            String cityCode = routeParam.getLocation_code();
            Map<LocalDate, List<ActivityInfo>> dailyActivities = new HashMap<>();
            int totalDays = routeParam.getStay_days();
            
            for (int day = 0; day < totalDays; day++) {
                LocalDate date = currentDate.plusDays(day);
                List<ActivityInfo> dayActivities = new ArrayList<>();
                
                if (!activities.isEmpty()) {
                    // 随机选择3-6个活动
                    int targetCount = 3 + random.nextInt(4); // 3到6个活动
                    
                    // 依次从activities列表中取活动
                    for (int i = 0; i < targetCount; i++) {
                        if (activityIndex >= activities.size()) {
                            activityIndex = 0; // 重新开始循环
                        }
                        dayActivities.add(activities.get(activityIndex));
                        activityIndex++;
                    }
                }
                
                dailyActivities.put(date, dayActivities);
                log.debug("Day {}: {} activities assigned for city {}", date, dayActivities.size(), cityCode);
            }
            
            result.put(cityCode, dailyActivities);
            currentDate = currentDate.plusDays(totalDays);
        }
        
        log.debug("Final organization result: {} cities with daily activities", result.size());
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
        int targetCount = DEFAULT_ACTIVITIES_PER_DAY;
        
        // 到达日：减少活动数量
        if (flightAnalysis.getArrivalDate() != null && date.equals(flightAnalysis.getArrivalDate())) {
            if (flightAnalysis.getArrivalTime() != null && flightAnalysis.getArrivalTime().isAfter(LocalTime.of(15, 0))) {
                targetCount = MIN_ACTIVITIES_PER_DAY; // 下午到达，只安排2个活动
            }
        }
        
        // 离开日：减少活动数量
        if (flightAnalysis.getDepartureDate() != null && date.equals(flightAnalysis.getDepartureDate())) {
            if (flightAnalysis.getDepartureTime() != null && flightAnalysis.getDepartureTime().isBefore(LocalTime.of(15, 0))) {
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
     * 简单的fallback筛选
     */
    private List<ActivityInfo> fallbackActivityFiltering(List<ActivityInfo> activities, GeneratePlanParam param) {
        log.info("Using fallback activity filtering");
        
        // 按评分排序，每个城市选择合适数量的活动
        Map<String, List<ActivityInfo>> activitiesByCity = activities.stream()
            .collect(Collectors.groupingBy(ActivityInfo::getCityCode));
        
        List<ActivityInfo> result = new ArrayList<>();
        
        for (var routeParam : param.getTrip_routes()) {
            String cityCode = routeParam.getLocation_code();
            List<ActivityInfo> cityActivities = activitiesByCity.getOrDefault(cityCode, new ArrayList<>());
            
            int totalActivities = Math.min(
                routeParam.getStay_days() * DEFAULT_ACTIVITIES_PER_DAY,
                cityActivities.size()
            );
            
            List<ActivityInfo> selectedActivities = cityActivities.stream()
                .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
                .limit(totalActivities)
                .collect(Collectors.toList());
            
            result.addAll(selectedActivities);
        }
        
        return result;
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
