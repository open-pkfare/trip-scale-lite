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
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationRequest;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationResponse;
import com.pkfare.trip.scale.util.JsonUtil;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
    private GlobalActivityAllocationResponseParser globalResponseParser;


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
            log.info("inputContent:{}",inputContent);
            
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
