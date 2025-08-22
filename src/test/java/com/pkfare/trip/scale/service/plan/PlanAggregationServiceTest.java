package com.pkfare.trip.scale.service.plan;

import com.pkfare.trip.scale.model.enums.PlanStatus;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.DailySchedule;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * PlanAggregationService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class PlanAggregationServiceTest {

    @Mock
    private ActivitySearchService activitySearchService;

    @InjectMocks
    private PlanAggregationService planAggregationService;

    private GeneratePlanParam testParam;
    private List<FlightInfo> testFlights;
    private List<HotelInfo> testHotels;
    private List<ActivityInfo> testActivities;
    private String testAiPlan;

    @BeforeEach
    void setUp() {
        testParam = createTestParam();
        testFlights = createTestFlights();
        testHotels = createTestHotels();
        testActivities = createTestActivities();
        testAiPlan = "AI generated travel plan";
    }

    @Test
    void testAggregateTripPlan_Success() {
        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, testActivities, testAiPlan);

        // Then
        assertNotNull(result);
        assertNotNull(result.getPlanId());
        assertEquals(PlanStatus.SUCCESS, result.getStatus());
        assertEquals("CNY", result.getCurrency());
        assertNotNull(result.getCreatedTime());
        assertEquals(testFlights, result.getFlights());
        assertEquals(testHotels, result.getHotels());
        assertEquals(testActivities, result.getActivities());
        assertEquals(testAiPlan, result.getAiGeneratedPlan());
        
        // 验证总费用计算
        BigDecimal expectedTotalCost = new BigDecimal("2000.00") // flight
            .add(new BigDecimal("1000.00")) // hotel
            .add(new BigDecimal("100.00")); // activity
        assertEquals(expectedTotalCost, result.getTotalCost());
        
        // 验证每日行程安排
        assertNotNull(result.getDailySchedules());
        assertEquals(testParam.getTrip_days(), result.getDailySchedules().size());
    }

    @Test
    void testAggregateTripPlan_OverBudget() {
        // Given - 设置较低的预算
        testParam.setBudgets("1000");

        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, testActivities, testAiPlan);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.OVER_BUDGET, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("超出预算"));
    }

    @Test
    void testAggregateTripPlan_NoAvailableOptions() {
        // Given - 没有航班和酒店
        List<FlightInfo> emptyFlights = Collections.emptyList();
        List<HotelInfo> emptyHotels = Collections.emptyList();

        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, emptyFlights, emptyHotels, testActivities, testAiPlan);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.NO_AVAILABLE_OPTION, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("未找到可用的航班或酒店选项"));
    }

    @Test
    void testAggregateTripPlan_NullInputs() {
        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, null, null, null, null);

        // Then
        assertNotNull(result);
        assertEquals(PlanStatus.NO_AVAILABLE_OPTION, result.getStatus());
        assertNotNull(result.getFlights());
        assertNotNull(result.getHotels());
        assertNotNull(result.getActivities());
        assertTrue(result.getFlights().isEmpty());
        assertTrue(result.getHotels().isEmpty());
        assertTrue(result.getActivities().isEmpty());
    }

    @Test
    void testBuildDailySchedules() {
        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, testActivities, testAiPlan);

        // Then
        List<DailySchedule> dailySchedules = result.getDailySchedules();
        assertNotNull(dailySchedules);
        assertEquals(testParam.getTrip_days(), dailySchedules.size());
        
        // 验证第一天的行程
        DailySchedule firstDay = dailySchedules.get(0);
        assertEquals(LocalDate.of(2025, 10, 1), firstDay.getDate());
        assertEquals("FCO", firstDay.getCityCode());
        assertEquals("Rome", firstDay.getCityName());
        assertNotNull(firstDay.getHotel());
        assertNotNull(firstDay.getActivities());
        assertNotNull(firstDay.getDailyCost());
        assertTrue(firstDay.getDailyCost().compareTo(BigDecimal.ZERO) > 0);
        
        // 验证日期连续性
        for (int i = 1; i < dailySchedules.size(); i++) {
            LocalDate currentDate = dailySchedules.get(i).getDate();
            LocalDate previousDate = dailySchedules.get(i - 1).getDate();
            assertEquals(1, currentDate.toEpochDay() - previousDate.toEpochDay());
        }
    }

    @Test
    void testCalculateTotalCost_AllComponents() {
        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, testActivities, testAiPlan);

        // Then
        BigDecimal expectedTotal = new BigDecimal("2000.00") // flight
            .add(new BigDecimal("1000.00")) // hotel
            .add(new BigDecimal("100.00")); // activity
        assertEquals(expectedTotal, result.getTotalCost());
    }

    @Test
    void testCalculateTotalCost_OnlyFlights() {
        // Given
        List<HotelInfo> emptyHotels = Collections.emptyList();
        List<ActivityInfo> emptyActivities = Collections.emptyList();

        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, emptyHotels, emptyActivities, testAiPlan);

        // Then
        assertEquals(new BigDecimal("2000.00"), result.getTotalCost());
    }

    @Test
    void testCalculateTotalCost_ZeroCost() {
        // Given
        List<FlightInfo> emptyFlights = Collections.emptyList();
        List<HotelInfo> emptyHotels = Collections.emptyList();
        List<ActivityInfo> emptyActivities = Collections.emptyList();

        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, emptyFlights, emptyHotels, emptyActivities, testAiPlan);

        // Then
        assertEquals(BigDecimal.ZERO, result.getTotalCost());
    }

    @Test
    void testDailySchedule_ActivityAllocation() {
        // Given - 多个活动
        List<ActivityInfo> multipleActivities = Arrays.asList(
            createActivityInfo("activity-1", "ROM", 4.5),
            createActivityInfo("activity-2", "ROM", 4.0),
            createActivityInfo("activity-3", "MIL", 4.8),
            createActivityInfo("activity-4", "MIL", 3.5)
        );

        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, multipleActivities, testAiPlan);

        // Then
        List<DailySchedule> dailySchedules = result.getDailySchedules();
        assertNotNull(dailySchedules);
        
        // 验证活动分配到各天
        int totalActivitiesInSchedule = dailySchedules.stream()
            .mapToInt(schedule -> schedule.getActivities() != null ? schedule.getActivities().size() : 0)
            .sum();
        
        assertTrue(totalActivitiesInSchedule > 0);
    }

    @Test
    void testDailySchedule_HotelAllocation() {
        // When
        TripPlan result = planAggregationService.aggregateTripPlan(
            testParam, testFlights, testHotels, testActivities, testAiPlan);

        // Then
        List<DailySchedule> dailySchedules = result.getDailySchedules();
        
        // 验证每天都有酒店分配
        for (DailySchedule schedule : dailySchedules) {
            if (schedule.getCityCode().equals("FCO")) {
                assertNotNull(schedule.getHotel());
                assertEquals("hotel-rome", schedule.getHotel().getHotelId());
            } else if (schedule.getCityCode().equals("MXP")) {
                assertNotNull(schedule.getHotel());
                assertEquals("hotel-milan", schedule.getHotel().getHotelId());
            }
        }
    }

    private GeneratePlanParam createTestParam() {
        GeneratePlanParam param = new GeneratePlanParam();
        param.setOrigin("Shenzhen");
        param.setStart_period("2025-10-01");
        param.setEnd_period("2025-10-14");
        param.setTrip_days(14);
        param.setAdult_number(1);
        param.setChild_number(1);
        param.setBudgets("15000");
        param.setCurrency("CNY");
        param.setRoom_quantity(1);
        
        TripRouteParam route1 = new TripRouteParam();
        route1.setDestination_city("Rome");
        route1.setLocation_code("FCO");
        route1.setStay_days(7);
        route1.setReason_for_recommendation("Historic city");
        
        TripRouteParam route2 = new TripRouteParam();
        route2.setDestination_city("Milan");
        route2.setLocation_code("MXP");
        route2.setStay_days(7);
        route2.setReason_for_recommendation("Fashion capital");
        
        param.setTrip_routes(Arrays.asList(route1, route2));
        return param;
    }

    private List<FlightInfo> createTestFlights() {
        FlightInfo flight = new FlightInfo();
        flight.setOneWay(false);
        flight.setTotal("2000.00");
        flight.setCurrency("CNY");
        return Collections.singletonList(flight);
    }

    private List<HotelInfo> createTestHotels() {
        HotelInfo hotel1 = new HotelInfo();
        hotel1.setHotelId("hotel-rome");
        hotel1.setHotelName("Rome Hotel");
        hotel1.setCityCode("FCO");
        hotel1.setTotalPrice(new BigDecimal("500.00"));
        hotel1.setCurrency("CNY");
        hotel1.setCheckInDate(LocalDate.of(2025, 10, 1));
        hotel1.setCheckOutDate(LocalDate.of(2025, 10, 8));
        
        HotelInfo hotel2 = new HotelInfo();
        hotel2.setHotelId("hotel-milan");
        hotel2.setHotelName("Milan Hotel");
        hotel2.setCityCode("MXP");
        hotel2.setTotalPrice(new BigDecimal("500.00"));
        hotel2.setCurrency("CNY");
        hotel2.setCheckInDate(LocalDate.of(2025, 10, 8));
        hotel2.setCheckOutDate(LocalDate.of(2025, 10, 15));
        
        return Arrays.asList(hotel1, hotel2);
    }

    private List<ActivityInfo> createTestActivities() {
        ActivityInfo activity = createActivityInfo("activity-1", "ROM", 4.5);
        return Collections.singletonList(activity);
    }

    private ActivityInfo createActivityInfo(String id, String cityCode, double rating) {
        ActivityInfo activity = new ActivityInfo();
        activity.setActivityId(id);
        activity.setName("Test Activity " + id);
        activity.setCityCode(cityCode);
        activity.setRating(rating);
        activity.setPrice(new BigDecimal("100.00"));
        activity.setCurrency("CNY");
        activity.setType("Museum");
        return activity;
    }
}
