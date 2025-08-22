package com.pkfare.trip.scale.service.plan;

import com.pkfare.trip.scale.model.enums.PlanStatus;
import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.external.ai.GeminiPlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * GeneratePlanService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class GeneratePlanServiceTest {

    @Mock
    private FlightSearchService flightSearchService;
    
    @Mock
    private HotelSearchService hotelSearchService;
    
    @Mock
    private ActivitySearchService activitySearchService;
    
    @Mock
    private GeminiPlanningService geminiPlanningService;
    
    @Mock
    private PlanAggregationService planAggregationService;

    @InjectMocks
    private GeneratePlanService generatePlanService;

    private GeneratePlanParam validParam;
    private List<FlightInfo> mockFlights;
    private List<HotelInfo> mockHotels;
    private List<ActivityInfo> mockActivities;

    @BeforeEach
    void setUp() {
        validParam = createValidGeneratePlanParam();
        mockFlights = createMockFlights();
        mockHotels = createMockHotels();
        mockActivities = createMockActivities();
    }

    @Test
    void testGeneratePlan_Success() {
        // Given
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        
        when(flightSearchService.searchFlights(any(), anyBoolean(), anyBoolean()))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenReturn("AI generated plan");
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.SUCCESS, result.getStatus());
        assertEquals("test-plan-id", result.getPlanId());
        
        verify(flightSearchService).searchFlights(eq(validParam), anyBoolean(), anyBoolean());
        verify(hotelSearchService).searchHotels(eq(validParam), eq(mockFlights));
        verify(activitySearchService).searchActivities(eq(mockHotels));
        verify(geminiPlanningService).generateAiPlan(any());
        verify(planAggregationService).aggregateTripPlan(
            eq(validParam), eq(mockFlights), eq(mockHotels), eq(mockActivities), eq("AI generated plan"));
    }

    @Test
    void testGeneratePlan_PreciseTravel_True() {
        // Given - 精确时间：end_period - start_period = trip_days
        validParam.setStart_period("2025-10-01");
        validParam.setEnd_period("2025-10-15");
        validParam.setTrip_days(14);
        
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        
        when(flightSearchService.searchFlights(any(), eq(true), anyBoolean()))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenReturn("AI generated plan");
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        verify(flightSearchService).searchFlights(eq(validParam), eq(true), anyBoolean());
    }

    @Test
    void testGeneratePlan_PreciseTravel_False() {
        // Given - 非精确时间：end_period - start_period != trip_days
        validParam.setStart_period("2025-10-01");
        validParam.setEnd_period("2025-10-20");
        validParam.setTrip_days(14);
        
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        
        when(flightSearchService.searchFlights(any(), eq(false), anyBoolean()))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenReturn("AI generated plan");
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        verify(flightSearchService).searchFlights(eq(validParam), eq(false), anyBoolean());
    }

    @Test
    void testGeneratePlan_RoundTrip_True() {
        // Given - 往返行程：第一个和最后一个目的地相同
        TripRouteParam route1 = createTripRoute("Rome", "IT", "FCO", 7);
        TripRouteParam route2 = createTripRoute("Florence", "IT", "FLR", 7);
        validParam.setTrip_routes(Arrays.asList(route1, route2));
        
        // 修改为相同的location_code以测试往返
        route1.setLocation_code("FCO");
        route2.setLocation_code("FCO");
        
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        
        when(flightSearchService.searchFlights(any(), anyBoolean(), eq(true)))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenReturn("AI generated plan");
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        verify(flightSearchService).searchFlights(eq(validParam), anyBoolean(), eq(true));
    }

    @Test
    void testGeneratePlan_RoundTrip_False() {
        // Given - 非往返行程：第一个和最后一个目的地不同
        TripRouteParam route1 = createTripRoute("Rome", "IT", "FCO", 7);
        TripRouteParam route2 = createTripRoute("Milan", "IT", "MXP", 7);
        validParam.setTrip_routes(Arrays.asList(route1, route2));
        
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        
        when(flightSearchService.searchFlights(any(), anyBoolean(), eq(false)))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenReturn("AI generated plan");
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        verify(flightSearchService).searchFlights(eq(validParam), anyBoolean(), eq(false));
    }

    @Test
    void testGeneratePlan_ExceptionHandling() {
        // Given
        when(flightSearchService.searchFlights(any(), anyBoolean(), anyBoolean()))
            .thenThrow(new RuntimeException("Flight search failed"));

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.API_ERROR, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Flight search failed"));
        assertNotNull(result.getPlanId());
        assertNotNull(result.getCreatedTime());
    }

    @Test
    void testGeneratePlan_AiPlanGenerationFails() {
        // Given
        when(flightSearchService.searchFlights(any(), anyBoolean(), anyBoolean()))
            .thenReturn(mockFlights);
        when(hotelSearchService.searchHotels(any(), any()))
            .thenReturn(mockHotels);
        when(activitySearchService.searchActivities(any()))
            .thenReturn(mockActivities);
        when(geminiPlanningService.generateAiPlan(any()))
            .thenThrow(new RuntimeException("AI service unavailable"));
        
        TripPlan expectedPlan = createMockTripPlan(PlanStatus.SUCCESS);
        when(planAggregationService.aggregateTripPlan(any(), any(), any(), any(), any()))
            .thenReturn(expectedPlan);

        // When
        TripPlan result = generatePlanService.generatePlan(validParam);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.SUCCESS, result.getStatus());
        
        // 验证AI计划生成失败时使用默认文本
        verify(planAggregationService).aggregateTripPlan(
            eq(validParam), eq(mockFlights), eq(mockHotels), eq(mockActivities), 
            eq("AI计划生成暂时不可用，但您的航班、酒店和活动信息已成功获取。"));
    }

    private GeneratePlanParam createValidGeneratePlanParam() {
        GeneratePlanParam param = new GeneratePlanParam();
        param.setOrigin("Shenzhen");
        param.setLocation_code("CN");
        param.setStart_period("2025-10-01");
        param.setEnd_period("2025-10-14");
        param.setTrip_days(14);
        param.setAdult_number(1);
        param.setChild_number(1);
        param.setBudgets("15000");
        param.setCurrency("CNY");
        param.setRoom_quantity(2);
        
        TripRouteParam route1 = createTripRoute("Rome", "IT", "FCO", 4);
        TripRouteParam route2 = createTripRoute("Ostia", "IT", "OST", 5);
        TripRouteParam route3 = createTripRoute("Anzio", "IT", "ANZ", 5);
        
        param.setTrip_routes(Arrays.asList(route1, route2, route3));
        return param;
    }

    private TripRouteParam createTripRoute(String city, String country, String locationCode, int stayDays) {
        TripRouteParam route = new TripRouteParam();
        route.setDestination_city(city);
        route.setCountry_code(country);
        route.setLocation_code(locationCode);
        route.setStay_days(stayDays);
        route.setReason_for_recommendation("Test recommendation");
        return route;
    }

    private List<FlightInfo> createMockFlights() {
        FlightInfo flight = new FlightInfo();
        flight.setOneWay(false);
        flight.setTotal("2000.00");
        flight.setCurrency("CNY");
        return Collections.singletonList(flight);
    }

    private List<HotelInfo> createMockHotels() {
        HotelInfo hotel = new HotelInfo();
        hotel.setHotelId("hotel-1");
        hotel.setHotelName("Test Hotel");
        hotel.setTotalPrice(new BigDecimal("1000.00"));
        hotel.setCurrency("CNY");
        return Collections.singletonList(hotel);
    }

    private List<ActivityInfo> createMockActivities() {
        ActivityInfo activity = new ActivityInfo();
        activity.setActivityId("activity-1");
        activity.setName("Test Activity");
        activity.setPrice(new BigDecimal("100.00"));
        activity.setCurrency("CNY");
        return Collections.singletonList(activity);
    }

    private TripPlan createMockTripPlan(PlanStatus status) {
        TripPlan plan = new TripPlan();
        plan.setPlanId("test-plan-id");
        plan.setStatus(status);
        plan.setTotalCost(new BigDecimal("3100.00"));
        plan.setCurrency("CNY");
        plan.setFlights(mockFlights);
        plan.setHotels(mockHotels);
        plan.setActivities(mockActivities);
        return plan;
    }
}
