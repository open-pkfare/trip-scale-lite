package com.pkfare.trip.scale.plan.service;

import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.external.ai.GeminiPlanningService;
import com.pkfare.trip.scale.service.external.ai.GoogleAiService;
import com.pkfare.trip.scale.service.plan.ActivitySearchService;
import com.pkfare.trip.scale.service.plan.FlightSearchService;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import com.pkfare.trip.scale.service.plan.PlanAggregationService;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.ValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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
    private GeminiPlanningService geminiPlanningService;

    @Autowired
    private GoogleAiService googleAiService;
    
    @Autowired
    private PlanAggregationService planAggregationService;

    /**
     * 生成旅行计划主入口
     * 
     * @param param 生成计划参数
     * @return 完整的旅行计划
     */
    public TripPlan generatePlan(GeneratePlanParam param) {
        log.info("Starting to generate trip plan for origin: {}, destinations: {}", 
            param.getOrigin(), param.getTrip_routes().size());
        
        try {
            // 1. 参数验证
            validateParams(param);
            
            // 2. 计算 preciseTravel 和 roundTrip
            boolean preciseTravel = calculatePreciseTravel(param);
            boolean roundTrip = calculateRoundTrip(param);
            
            log.info("Trip configuration: preciseTravel={}, roundTrip={}", preciseTravel, roundTrip);
            
            // 3. 搜索航班
            List<FlightInfo> flights = flightSearchService.searchFlights(param, preciseTravel, roundTrip);
            log.info("Found {} flights", flights.size());
            
            // 4. 搜索酒店
            List<HotelInfo> hotels = hotelSearchService.searchHotels(param, flights);
            log.info("Found {} hotels", hotels.size());
            
            // 5. 搜索活动
            List<ActivityInfo> activities = activitySearchService.searchActivities(hotels);
            log.info("Found {} activities", activities.size());
            
            // 6. AI 生成计划
            String aiGeneratedPlan = generateAiPlan(param, flights, hotels, activities);
            log.info("AI plan generated, length: {} characters", 
                aiGeneratedPlan != null ? aiGeneratedPlan.length() : 0);
            
            // 7. 聚合结果
            TripPlan tripPlan = planAggregationService.aggregateTripPlan(
                param, flights, hotels, activities, aiGeneratedPlan);
            
            log.info("Trip plan generated successfully: planId={}, status={}", 
                tripPlan.getPlanId(), tripPlan.getStatus());
            
            return tripPlan;
            
        } catch (Exception e) {
            log.error("Failed to generate trip plan", e);
            
            // 返回错误状态的计划
            TripPlan errorPlan = new TripPlan();
            errorPlan.setPlanId(java.util.UUID.randomUUID().toString());
            errorPlan.setStatus(com.pkfare.trip.scale.model.enums.PlanStatus.API_ERROR);
            errorPlan.setErrorMessage("生成旅行计划时发生错误: " + e.getMessage());
            errorPlan.setCreatedTime(java.time.LocalDateTime.now());
            errorPlan.setCurrency(param.getCurrency());
            
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
        long daysBetween = DateUtil.daysBetween(startDate, endDate);
        
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
     * 生成AI计划
     * 
     * @param param 参数
     * @param flights 航班信息
     * @param hotels 酒店信息
     * @param activities 活动信息
     * @return AI生成的计划文本
     */
    private String generateAiPlan(GeneratePlanParam param, 
                                 List<FlightInfo> flights, 
                                 List<HotelInfo> hotels, 
                                 List<ActivityInfo> activities) {
        try {
            SubmitAiPlanInfo planInfo = new SubmitAiPlanInfo();
            planInfo.setGeneratePlanParam(param);
            planInfo.setFlightInfos(flights);
            planInfo.setHotelInfos(hotels);
            planInfo.setActivityInfos(activities);

            // return geminiPlanningService.generateAiPlan(planInfo);
            googleAiService.generateAiPlan(planInfo);
            return null;
        } catch (Exception e) {
            log.error("Failed to generate AI plan", e);
            return "AI计划生成暂时不可用，但您的航班、酒店和活动信息已成功获取。";
        }
    }
}
