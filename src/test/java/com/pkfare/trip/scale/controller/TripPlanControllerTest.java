package com.pkfare.trip.scale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.model.enums.PlanStatus;
import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TripPlanController单元测试
 * 
 * @author Trip Scale Team
 */
@WebMvcTest(TripPlanController.class)
public class TripPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeneratePlanService generatePlanService;

    @Autowired
    private ObjectMapper objectMapper;

    private GeneratePlanParam validParam;
    private TripPlan successPlan;

    @BeforeEach
    void setUp() {
        validParam = createValidParam();
        successPlan = createSuccessPlan();
    }

    @Test
    void testGeneratePlan_Success() throws Exception {
        // Given
        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenReturn(successPlan);

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.planId").value("test-plan-id"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalCost").value(3100.00))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.flights").isArray())
                .andExpect(jsonPath("$.hotels").isArray())
                .andExpect(jsonPath("$.activities").isArray());
    }

    @Test
    void testGeneratePlan_OverBudget() throws Exception {
        // Given
        TripPlan overBudgetPlan = createOverBudgetPlan();
        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenReturn(overBudgetPlan);

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVER_BUDGET"))
                .andExpect(jsonPath("$.errorMessage").exists())
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("超出预算")));
    }

    @Test
    void testGeneratePlan_NoAvailableOptions() throws Exception {
        // Given
        TripPlan noOptionsPlan = createNoOptionsPlan();
        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenReturn(noOptionsPlan);

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_AVAILABLE_OPTION"))
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    void testGeneratePlan_ServiceException() throws Exception {
        // Given
        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenThrow(new RuntimeException("Service error"));

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("API_ERROR"))
                .andExpect(jsonPath("$.errorMessage").exists())
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("Service error")));
    }

    @Test
    void testGeneratePlan_InvalidJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGeneratePlan_MissingRequiredFields() throws Exception {
        // Given
        GeneratePlanParam invalidParam = new GeneratePlanParam();
        // 不设置必需字段

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidParam)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGeneratePlan_EmptyRequestBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGeneratePlan_WrongContentType() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.TEXT_PLAIN)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void testGeneratePlan_WrongHttpMethod() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validParam)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void testHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/trip-plan/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Trip Plan Service is running"));
    }

    @Test
    void testGeneratePlan_LargePayload() throws Exception {
        // Given - 创建一个包含很多路线的大请求
        GeneratePlanParam largeParam = createValidParam();
        for (int i = 0; i < 50; i++) {
            TripRouteParam route = new TripRouteParam();
            route.setDestination_city("City" + i);
            route.setLocation_code("C" + String.format("%02d", i));
            route.setCountry_code("IT");
            route.setStay_days(1);
            route.setReason_for_recommendation("Test city " + i);
            largeParam.getTrip_routes().add(route);
        }
        largeParam.setTrip_days(largeParam.getTrip_routes().stream().mapToInt(TripRouteParam::getStay_days).sum());

        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenReturn(successPlan);

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(largeParam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").exists());
    }

    @Test
    void testGeneratePlan_SpecialCharacters() throws Exception {
        // Given
        GeneratePlanParam paramWithSpecialChars = createValidParam();
        paramWithSpecialChars.setOrigin("深圳"); // 中文字符
        paramWithSpecialChars.getTrip_routes().get(0).setDestination_city("罗马");
        paramWithSpecialChars.getTrip_routes().get(0).setReason_for_recommendation("包含特殊字符: @#$%^&*()");

        when(generatePlanService.generatePlan(any(GeneratePlanParam.class)))
            .thenReturn(successPlan);

        // When & Then
        mockMvc.perform(post("/api/trip-plan/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paramWithSpecialChars)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").exists());
    }

    private GeneratePlanParam createValidParam() {
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
        
        TripRouteParam route1 = new TripRouteParam();
        route1.setStay_days(4);
        route1.setDestination_city("Rome");
        route1.setCountry_code("IT");
        route1.setLocation_code("FCO");
        route1.setReason_for_recommendation("Historic city");
        
        TripRouteParam route2 = new TripRouteParam();
        route2.setStay_days(5);
        route2.setDestination_city("Ostia");
        route2.setCountry_code("IT");
        route2.setLocation_code("OST");
        route2.setReason_for_recommendation("Ancient ruins");
        
        TripRouteParam route3 = new TripRouteParam();
        route3.setStay_days(5);
        route3.setDestination_city("Anzio");
        route3.setCountry_code("IT");
        route3.setLocation_code("ANZ");
        route3.setReason_for_recommendation("Seaside town");
        
        param.setTrip_routes(Arrays.asList(route1, route2, route3));
        return param;
    }

    private TripPlan createSuccessPlan() {
        TripPlan plan = new TripPlan();
        plan.setPlanId("test-plan-id");
        plan.setStatus(PlanStatus.SUCCESS);
        plan.setTotalCost(new BigDecimal("3100.00"));
        plan.setCurrency("CNY");
        plan.setFlights(Collections.emptyList());
        plan.setHotels(Collections.emptyList());
        plan.setActivities(Collections.emptyList());
        plan.setDailySchedules(Collections.emptyList());
        plan.setAiGeneratedPlan("AI generated plan");
        plan.setCreatedTime(LocalDateTime.now());
        return plan;
    }

    private TripPlan createOverBudgetPlan() {
        TripPlan plan = new TripPlan();
        plan.setPlanId("over-budget-plan-id");
        plan.setStatus(PlanStatus.OVER_BUDGET);
        plan.setTotalCost(new BigDecimal("20000.00"));
        plan.setCurrency("CNY");
        plan.setErrorMessage("计划总费用 20000.00 超出预算 15000.00，超出金额：5000.00");
        plan.setCreatedTime(LocalDateTime.now());
        return plan;
    }

    private TripPlan createNoOptionsPlan() {
        TripPlan plan = new TripPlan();
        plan.setPlanId("no-options-plan-id");
        plan.setStatus(PlanStatus.NO_AVAILABLE_OPTION);
        plan.setTotalCost(BigDecimal.ZERO);
        plan.setCurrency("CNY");
        plan.setErrorMessage("未找到可用的航班或酒店选项，请调整搜索条件或时间");
        plan.setCreatedTime(LocalDateTime.now());
        return plan;
    }
}
