package com.pkfare.trip.scale.service.external.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.*;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.*;
import com.pkfare.trip.scale.util.JsonUtil;
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

    public GoogleAiService() {
        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(GoogleConfig.GOOGLE_API_KEY)
                .build();
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
            
            // 添加该城市的酒店
            if (planInfo.getHotelInfos() != null) {
                List<HotelInfo> cityHotels = planInfo.getHotelInfos().stream()
                        .filter(hotel -> cityCode.equals(hotel.getCityCode()))
                        .collect(Collectors.toList());
                cityData.setHotels(cityHotels);
            }
            
            // 添加该城市的活动
            if (planInfo.getActivityInfos() != null) {
                List<ActivityInfo> cityActivities = planInfo.getActivityInfos().stream()
                        .filter(activity -> cityData.getCityName().equals(activity.getCityCode()))
                        .collect(Collectors.toList());
                cityData.setActivities(cityActivities);
            }
        }
        
        return cityPlanMap;
    }

    /**
     * 生成每日路线规划
     */
    private List<DailyRoutePlan> generateDailyRoutePlans(Map<String, CityPlanData> cityPlanMap, 
                                                        GeneratePlanParam param) throws Exception {
        List<DailyRoutePlan> dailyPlans = new ArrayList<>();
        LocalDate currentDate = LocalDate.parse(param.getStart_period());
        
        for (TripRouteParam routeParam : param.getTrip_routes()) {
            String cityCode = routeParam.getLocation_code();
            CityPlanData cityData = cityPlanMap.get(cityCode);
            
            if (cityData == null) {
                continue;
            }
            
            // 为该城市的每一天生成路线规划
            for (int day = 0; day < routeParam.getStay_days(); day++) {
                DailyRoutePlan dailyPlan = generateSingleDayPlan(cityData, currentDate.plusDays(day), day);
                dailyPlans.add(dailyPlan);
            }
            
            currentDate = currentDate.plusDays(routeParam.getStay_days());
        }
        
        return dailyPlans;
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
     */
    private List<ActivityInfo> selectDayActivities(List<ActivityInfo> allActivities, int dayIndex, int totalDays) {
        if (allActivities == null || allActivities.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 按评分排序
        allActivities.sort((a, b) -> Double.compare(b.getRating(), a.getRating()));
        
        // 平均分配活动到各天，每天最多3个活动
        int activitiesPerDay = Math.min(3, Math.max(1, allActivities.size() / totalDays));
        int startIndex = dayIndex * activitiesPerDay;
        int endIndex = Math.min(startIndex + activitiesPerDay, allActivities.size());
        
        if (startIndex >= allActivities.size()) {
            return new ArrayList<>();
        }

        List<ActivityInfo> copy = ImmutableList.copyOf(allActivities);
        return copy.subList(startIndex, endIndex);
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
    private RouteSegment calculateRoute(LocationPoint start, LocationPoint end) throws Exception {
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
                segment.setRoutesJson(JsonUtil.toJson(result.routes));
                
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
                if (i > 0) notes.append(", ");
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
        for(DailyRoutePlan plan :dailyPlans){
            if(plan.getActivities() != null){
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
     * 城市计划数据内部类
     */
    private static class CityPlanData {
        private String cityCode;
        private String cityName;
        private int stayDays;
        private List<HotelInfo> hotels;
        private List<ActivityInfo> activities;

        // Getters and Setters
        public String getCityCode() { return cityCode; }
        public void setCityCode(String cityCode) { this.cityCode = cityCode; }
        public String getCityName() { return cityName; }
        public void setCityName(String cityName) { this.cityName = cityName; }
        public int getStayDays() { return stayDays; }
        public void setStayDays(int stayDays) { this.stayDays = stayDays; }
        public List<HotelInfo> getHotels() { return hotels; }
        public void setHotels(List<HotelInfo> hotels) { this.hotels = hotels; }
        public List<ActivityInfo> getActivities() { return activities; }
        public void setActivities(List<ActivityInfo> activities) { this.activities = activities; }
    }

    /**
     * 位置点内部类
     */
    private static class LocationPoint {
        private String name;
        private double latitude;
        private double longitude;

        public LocationPoint(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters
        public String getName() { return name; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}
