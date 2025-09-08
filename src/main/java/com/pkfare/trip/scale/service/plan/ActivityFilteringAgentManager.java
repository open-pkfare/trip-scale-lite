package com.pkfare.trip.scale.service.plan;

import com.google.adk.agents.BaseAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.agent.planning.ActivityFilteringAgent;
import com.pkfare.trip.scale.agent.planning.ActivityFilteringPrompt;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringRequest;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringResponse;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationRequest;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationResponse;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 活动筛选Agent管理器
 * 负责管理Google ADK Agent的会话和调用
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Component
public class ActivityFilteringAgentManager {
    
    @Autowired
    private ActivityFilteringResponseParser responseParser;
    
    @Autowired
    private GlobalActivityAllocationResponseParser globalResponseParser;
    
    private static final int AGENT_TIMEOUT_SECONDS = 30;
    private static final String SESSION_PREFIX = "activity_filtering_";
    
    /**
     * 使用Google ADK Agent进行活动筛选
     * 
     * @param request 筛选请求
     * @return 筛选响应
     */
    public ActivityFilteringResponse filterActivitiesWithAgent(ActivityFilteringRequest request) {
        log.info("Starting AI activity filtering for city {} on {}", 
            request.getCityCode(), request.getDate());
        try {
            // 1. 创建Agent实例
            BaseAgent agent = ActivityFilteringAgent.instance();
            InMemoryRunner runner = new InMemoryRunner(agent);
            
            // 2. 创建会话
            String sessionId = generateSessionId(request);
            ConcurrentMap<String, Object> sessionState = createSessionState(request);
            
            Session session = runner.sessionService()
                .createSession("activity_filtering", sessionId, sessionState, "user123")
                .blockingGet();
            
            // 3. 构建输入内容
            String prompt = buildFilteringPrompt(request);
            Content inputContent = Content.fromParts(Part.fromText(prompt));
            
            // 4. 执行Agent调用
            log.debug("Sending prompt to agent: {}", prompt);
            Flowable<Event> events = runner.runAsync("activity_filtering", session.id(), inputContent);
            
            // 5. 收集响应
            StringBuilder responseBuilder = new StringBuilder();
            events.timeout(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .blockingForEach(event -> {
                    String content = event.stringifyContent();
                    responseBuilder.append(content).append("\n");
                    log.debug("Agent response event: {}", content);
                });
            
            String agentResponse = responseBuilder.toString().trim();
            log.info("Agent filtering completed for city {} on {}", 
                request.getCityCode(), request.getDate());
            
            // 6. 解析响应
            ActivityFilteringResponse response = responseParser.parseResponse(agentResponse, request.getCandidateActivities());
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to execute agent filtering for city {} on {}", 
                request.getCityCode(), request.getDate(), e);
            
            // 返回错误响应
            ActivityFilteringResponse errorResponse = new ActivityFilteringResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setErrorMessage("Agent execution failed: " + e.getMessage());
            return errorResponse;
        }
    }
    

    
    /**
     * 生成会话ID
     */
    private String generateSessionId(ActivityFilteringRequest request) {
        return SESSION_PREFIX + request.getCityCode() + "_" + request.getDate().toString() + "_" + System.currentTimeMillis();
    }
    
    /**
     * 创建会话状态
     */
    private ConcurrentMap<String, Object> createSessionState(ActivityFilteringRequest request) {
        ConcurrentMap<String, Object> state = Maps.newConcurrentMap();
        state.put("user:userId", "user123"); // 实际应该从请求中获取
        state.put("city_code", request.getCityCode());
        state.put("city_name", request.getCityName());
        state.put("date", request.getDate().toString());
        state.put("budget", request.getBudget());
        state.put("currency", request.getCurrency());
        
        if (request.getFlightInfo() != null) {
            if (request.getFlightInfo().getType() != null) {
                state.put("flight_type", request.getFlightInfo().getType());
            }
            if (request.getFlightInfo().getArrivalTime() != null) {
                state.put("arrival_time", request.getFlightInfo().getArrivalTime());
            }
            if (request.getFlightInfo().getDepartureTime() != null) {
                state.put("departure_time", request.getFlightInfo().getDepartureTime());
            }
        }
        
        return state;
    }
    
    /**
     * 构建筛选提示词
     */
    private String buildFilteringPrompt(ActivityFilteringRequest request) {
        try {
            // 构建航班信息JSON
            String flightInfoJson = JsonUtil.toJson(request.getFlightInfo());
            
            // 构建用户偏好JSON
            String userPreferencesJson = JsonUtil.toJson(request.getUserPreferences());
            
            // 构建活动列表JSON（简化版，只包含关键信息）
            String activitiesJson = buildSimplifiedActivitiesJson(request.getCandidateActivities());
            
            // 构建城市信息JSON
            String cityInfoJson = buildCityInfoJson(request);
            
            // 使用提示词模板
            return ActivityFilteringPrompt.buildFilteringPrompt(
                flightInfoJson, userPreferencesJson, activitiesJson, cityInfoJson);
                
        } catch (Exception e) {
            log.error("Failed to build filtering prompt", e);
            return "Please filter the provided activities based on flight schedule and user preferences.";
        }
    }
    
    /**
     * 构建简化的活动JSON（避免过长的输入）
     */
    private String buildSimplifiedActivitiesJson(List<ActivityInfo> activities) {
        try {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < activities.size(); i++) {
                if (i > 0) json.append(",");
                ActivityInfo activity = activities.get(i);
                json.append("{")
                    .append("\"id\":\"").append(activity.getActivityId() != null ? activity.getActivityId() : "").append("\",")
                    .append("\"name\":\"").append(activity.getName()).append("\",")
                    .append("\"description\":\"").append(activity.getDescription() != null ? activity.getDescription() : "").append("\",")
                    .append("\"rating\":").append(activity.getRating()).append(",")
                    .append("\"price\":").append(activity.getPrice()).append(",")
                    .append("\"cityCode\":\"").append(activity.getCityCode()).append("\"")
                    .append("}");
            }
            json.append("]");
            return json.toString();
        } catch (Exception e) {
            log.error("Failed to build simplified activities JSON", e);
            return "[]";
        }
    }
    
    /**
     * 构建城市信息JSON
     */
    private String buildCityInfoJson(ActivityFilteringRequest request) {
        try {
            return String.format(
                "{\"city_code\":\"%s\",\"city_name\":\"%s\",\"date\":\"%s\",\"budget\":\"%s\",\"currency\":\"%s\"}",
                request.getCityCode(),
                request.getCityName(),
                request.getDate().toString(),
                request.getBudget() != null ? request.getBudget() : "",
                request.getCurrency() != null ? request.getCurrency() : ""
            );
        } catch (Exception e) {
            log.error("Failed to build city info JSON", e);
            return "{}";
        }
    }
    
    /**
     * 使用Google ADK Agent进行全局活动分配
     * 
     * @param param 生成计划参数
     * @param request 全局分配请求
     * @return 全局分配响应
     */
    public GlobalActivityAllocationResponse allocateActivitiesGlobally(GeneratePlanParam param, GlobalActivityAllocationRequest request) {
        log.info("Starting global AI activity allocation for {} days trip", 
            request.getItinerary().getTotalDays());
        
        try {
            // 1. 创建Agent实例
            BaseAgent agent = ActivityFilteringAgent.instance();
            InMemoryRunner runner = new InMemoryRunner(agent);
            
            // 2. 为ActivityFilteringAgent创建专用会话
            String sessionId = "global_" + System.currentTimeMillis();
            ConcurrentMap<String, Object> sessionState = Maps.newConcurrentMap();
            String userId = "user123";
            sessionState.put("user:userId", userId);
            sessionState.put("total_days", request.getItinerary().getTotalDays());
            sessionState.put("total_activities", request.getAllActivities().size());

            // 创建专用于ActivityFilteringAgent的会话
            Session session = runner.sessionService()
                .createSession("activity_filtering_agent",userId, sessionState, sessionId )
                .blockingGet();
            
            log.debug("Created session for ActivityFilteringAgent: {}", session.id());

            // 3. 构建输入内容
            String prompt = buildGlobalAllocationPrompt(request);
            Content inputContent = Content.fromParts(Part.fromText(prompt));
            
            // 4. 执行Agent调用
            log.debug("Sending global allocation prompt to agent");
            Flowable<Event> events = runner.runAsync(userId, session.id(), inputContent);
            
            // 5. 收集响应
            StringBuilder responseBuilder = new StringBuilder();
            events.timeout(200, TimeUnit.SECONDS) // Longer timeout for global allocation
                .blockingForEach(event -> {
                    String content = event.stringifyContent();
                    responseBuilder.append(content).append("\n");
                    log.debug("Agent response event: {}", content);
                });
            
            String agentResponse = responseBuilder.toString().trim();
            log.info("Global activity allocation completed for {} days trip", 
                request.getItinerary().getTotalDays());
            
            // 6. 解析响应
            GlobalActivityAllocationResponse response = globalResponseParser.parseGlobalAllocationResponse(
                agentResponse, request.getAllActivities());
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to execute global activity allocation", e);
            
            // 返回错误响应
            GlobalActivityAllocationResponse errorResponse = new GlobalActivityAllocationResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setErrorMessage("Global allocation failed: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * 生成全局会话ID
     */
    private String generateGlobalSessionId(GlobalActivityAllocationRequest request) {
        return "global_allocation_" + request.getItinerary().getTotalDays() + "days_" + System.currentTimeMillis();
    }
    
    /**
     * 创建全局会话状态
     */
    private ConcurrentMap<String, Object> createGlobalSessionState(GlobalActivityAllocationRequest request) {
        ConcurrentMap<String, Object> state = Maps.newConcurrentMap();
        state.put("user:userId", "user123"); // Should be from request
        state.put("total_days", request.getItinerary().getTotalDays());
        state.put("total_activities", request.getAllActivities().size());
        state.put("budget", request.getBudget());
        state.put("currency", request.getCurrency());
        
        if (request.getFlightConstraints() != null) {
            if (request.getFlightConstraints().getArrivalDate() != null) {
                state.put("arrival_date", request.getFlightConstraints().getArrivalDate().toString());
            }
            if (request.getFlightConstraints().getDepartureDate() != null) {
                state.put("departure_date", request.getFlightConstraints().getDepartureDate().toString());
            }
        }
        
        return state;
    }
    
    /**
     * 构建全局分配提示词
     */
    private String buildGlobalAllocationPrompt(GlobalActivityAllocationRequest request) {
        try {
            // 构建所有活动的简化JSON
            String allActivitiesJson = buildSimplifiedActivitiesJson(request.getAllActivities());
            
            // 构建行程信息JSON
            String itineraryJson = JsonUtil.toJson(request.getItinerary());
            
            // 构建用户偏好JSON
            String userPreferencesJson = JsonUtil.toJson(request.getUserPreferences());
            
            // 构建航班约束JSON
            String flightConstraintsJson = JsonUtil.toJson(request.getFlightConstraints());
            
            // 使用全局分配提示词模板
            return ActivityFilteringPrompt.buildGlobalAllocationPrompt(
                allActivitiesJson, itineraryJson, userPreferencesJson, flightConstraintsJson);
                
        } catch (Exception e) {
            log.error("Failed to build global allocation prompt", e);
            return "Please create a daily activity allocation plan for the provided trip information.";
        }
    }
    
    /**
     * 检查Agent是否可用
     */
    public boolean isAgentAvailable() {
        try {
            BaseAgent agent = ActivityFilteringAgent.instance();
            return agent != null;
        } catch (Exception e) {
            log.warn("Agent is not available", e);
            return false;
        }
    }
}
