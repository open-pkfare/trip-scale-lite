package com.pkfare.trip.scale.service.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.service.plan.dto.ActivityFilteringResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google ADK Agent响应解析器
 * 将AI返回的文本解析为结构化的活动筛选结果
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Component
public class ActivityFilteringResponseParser {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // JSON块提取正则表达式
    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTIVITY_LIST_PATTERN = Pattern.compile("\\[\\s*\\{[\\s\\S]*?\\}\\s*\\]");
    
    /**
     * 解析Agent响应文本
     * 
     * @param agentResponse Agent返回的文本响应
     * @param originalActivities 原始活动列表（用于匹配）
     * @return 解析后的筛选响应
     */
    public ActivityFilteringResponse parseResponse(String agentResponse, List<ActivityInfo> originalActivities) {
        log.debug("Parsing agent response: {}", agentResponse);
        
        ActivityFilteringResponse response = new ActivityFilteringResponse();
        response.setStatus("SUCCESS");
        response.setRecommendedActivities(new ArrayList<>());
        
        try {
            // 1. 尝试提取JSON格式的响应
            List<ActivityInfo> parsedActivities = extractActivitiesFromJson(agentResponse, originalActivities);
            
            if (!parsedActivities.isEmpty()) {
                response.setRecommendedActivities(convertToRecommendedActivities(parsedActivities));
                response.setReasoning(extractReasoning(agentResponse));
                response.setTimeRecommendation(extractTimeRecommendation(agentResponse));
                response.setNotes(extractNotes(agentResponse));
                return response;
            }
            
            // 2. 尝试基于文本内容匹配活动
            List<ActivityInfo> textMatchedActivities = extractActivitiesFromText(agentResponse, originalActivities);
            
            if (!textMatchedActivities.isEmpty()) {
                response.setRecommendedActivities(convertToRecommendedActivities(textMatchedActivities));
                response.setReasoning(extractReasoning(agentResponse));
                response.setTimeRecommendation(extractTimeRecommendation(agentResponse));
                response.setNotes(extractNotes(agentResponse));
                return response;
            }
            
            // 3. 如果无法解析，返回基于规则的默认筛选
            log.warn("Unable to parse agent response, using default filtering");
            response.setStatus("FALLBACK");
            response.setErrorMessage("Unable to parse AI response, using rule-based filtering");
            
        } catch (Exception e) {
            log.error("Error parsing agent response", e);
            response.setStatus("ERROR");
            response.setErrorMessage("Failed to parse AI response: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 从JSON格式中提取活动
     */
    private List<ActivityInfo> extractActivitiesFromJson(String response, List<ActivityInfo> originalActivities) {
        List<ActivityInfo> activities = new ArrayList<>();
        
        try {
            // 提取JSON块
            Matcher jsonMatcher = JSON_PATTERN.matcher(response);
            if (jsonMatcher.find()) {
                String jsonContent = jsonMatcher.group(1);
                log.debug("Extracted JSON content: {}", jsonContent);
                
                // 尝试解析为活动列表
                JsonNode rootNode = objectMapper.readTree(jsonContent);
                
                // 检查是否有recommended_activities字段
                if (rootNode.has("recommended_activities")) {
                    JsonNode activitiesNode = rootNode.get("recommended_activities");
                    activities.addAll(parseActivitiesFromJsonNode(activitiesNode, originalActivities));
                } else if (rootNode.isArray()) {
                    // 直接是活动数组
                    activities.addAll(parseActivitiesFromJsonNode(rootNode, originalActivities));
                }
            }
            
            // 如果没有找到JSON块，尝试查找数组格式
            if (activities.isEmpty()) {
                Matcher arrayMatcher = ACTIVITY_LIST_PATTERN.matcher(response);
                if (arrayMatcher.find()) {
                    String arrayContent = arrayMatcher.group();
                    JsonNode arrayNode = objectMapper.readTree(arrayContent);
                    activities.addAll(parseActivitiesFromJsonNode(arrayNode, originalActivities));
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract activities from JSON", e);
        }
        
        return activities;
    }
    
    /**
     * 从JsonNode解析活动
     */
    private List<ActivityInfo> parseActivitiesFromJsonNode(JsonNode node, List<ActivityInfo> originalActivities) {
        List<ActivityInfo> activities = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode activityNode : node) {
                ActivityInfo activity = matchActivityFromNode(activityNode, originalActivities);
                if (activity != null) {
                    activities.add(activity);
                }
            }
        }
        
        return activities;
    }
    
    /**
     * 从JsonNode匹配原始活动
     */
    private ActivityInfo matchActivityFromNode(JsonNode node, List<ActivityInfo> originalActivities) {
        try {
            // 尝试通过名称匹配
            if (node.has("name")) {
                String name = node.get("name").asText();
                return findActivityByName(name, originalActivities);
            }
            
            // 尝试通过ID匹配
            if (node.has("id")) {
                String id = node.get("id").asText();
                return findActivityById(id, originalActivities);
            }
            
            // 尝试通过多个字段匹配
            if (node.has("activity_name")) {
                String name = node.get("activity_name").asText();
                return findActivityByName(name, originalActivities);
            }
            
        } catch (Exception e) {
            log.debug("Failed to match activity from node", e);
        }
        
        return null;
    }
    
    /**
     * 从文本内容中提取活动
     */
    private List<ActivityInfo> extractActivitiesFromText(String response, List<ActivityInfo> originalActivities) {
        List<ActivityInfo> activities = new ArrayList<>();
        
        // 查找活动名称的模式
        for (ActivityInfo activity : originalActivities) {
            if (response.toLowerCase().contains(activity.getName().toLowerCase())) {
                activities.add(activity);
                if (activities.size() >= 6) { // 最多6个活动
                    break;
                }
            }
        }
        
        return activities;
    }
    
    /**
     * 根据名称查找活动
     */
    private ActivityInfo findActivityByName(String name, List<ActivityInfo> activities) {
        return activities.stream()
            .filter(activity -> activity.getName().equalsIgnoreCase(name) || 
                              activity.getName().toLowerCase().contains(name.toLowerCase()) ||
                              name.toLowerCase().contains(activity.getName().toLowerCase()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 根据ID查找活动
     */
    private ActivityInfo findActivityById(String id, List<ActivityInfo> activities) {
        return activities.stream()
            .filter(activity -> id.equals(activity.getActivityId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 转换为推荐活动列表
     */
    private List<ActivityFilteringResponse.RecommendedActivity> convertToRecommendedActivities(List<ActivityInfo> activities) {
        List<ActivityFilteringResponse.RecommendedActivity> recommended = new ArrayList<>();
        
        for (int i = 0; i < activities.size(); i++) {
            ActivityInfo activity = activities.get(i);
            ActivityFilteringResponse.RecommendedActivity recActivity = new ActivityFilteringResponse.RecommendedActivity();
            recActivity.setActivity(activity);
            recActivity.setPriority(5 - i); // 优先级递减
            recActivity.setReason("AI recommended based on user preferences and flight schedule");
            recActivity.setSuggestedDuration(2.0); // 默认2小时
            recActivity.setMustVisit(i < 2); // 前2个为必游
            
            recommended.add(recActivity);
        }
        
        return recommended;
    }
    
    /**
     * 提取推荐理由
     */
    private String extractReasoning(String response) {
        // 查找推荐理由的关键词
        String[] reasoningKeywords = {"推荐理由", "理由", "原因", "建议", "recommendation", "reason", "because"};
        
        for (String keyword : reasoningKeywords) {
            int index = response.toLowerCase().indexOf(keyword.toLowerCase());
            if (index != -1) {
                // 提取该关键词后的一段文本
                String remaining = response.substring(index);
                String[] lines = remaining.split("\n");
                if (lines.length > 0) {
                    return lines[0].trim();
                }
            }
        }
        
        return "AI-based activity filtering applied";
    }
    
    /**
     * 提取时间建议
     */
    private String extractTimeRecommendation(String response) {
        // 查找时间建议的关键词
        String[] timeKeywords = {"时间建议", "游览时间", "时间安排", "time", "schedule", "timing"};
        
        for (String keyword : timeKeywords) {
            int index = response.toLowerCase().indexOf(keyword.toLowerCase());
            if (index != -1) {
                String remaining = response.substring(index);
                String[] lines = remaining.split("\n");
                if (lines.length > 0) {
                    return lines[0].trim();
                }
            }
        }
        
        return "Follow suggested activity sequence for optimal experience";
    }
    
    /**
     * 提取注意事项
     */
    private String extractNotes(String response) {
        // 查找注意事项的关键词
        String[] noteKeywords = {"注意事项", "提醒", "建议", "notes", "tips", "attention"};
        
        for (String keyword : noteKeywords) {
            int index = response.toLowerCase().indexOf(keyword.toLowerCase());
            if (index != -1) {
                String remaining = response.substring(index);
                String[] lines = remaining.split("\n");
                if (lines.length > 0) {
                    return lines[0].trim();
                }
            }
        }
        
        return "Consider weather conditions and local opening hours";
    }
}
