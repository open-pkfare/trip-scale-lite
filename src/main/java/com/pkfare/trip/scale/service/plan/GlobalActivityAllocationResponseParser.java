package com.pkfare.trip.scale.service.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.service.plan.dto.DailyActivityPlan;
import com.pkfare.trip.scale.service.plan.dto.GlobalActivityAllocationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global Activity Allocation Response Parser
 * Parses AI responses for complete trip activity allocation
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Component
public class GlobalActivityAllocationResponseParser {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // JSON block extraction patterns
    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAILY_PLANS_PATTERN = Pattern.compile("\"dailyPlans\"\\s*:\\s*\\[([\\s\\S]*?)\\]", Pattern.CASE_INSENSITIVE);
    
    /**
     * Parse AI response for global activity allocation
     * 
     * @param agentResponse AI response text
     * @param originalActivities Original activity list for matching
     * @return Parsed allocation response
     */
    public GlobalActivityAllocationResponse parseGlobalAllocationResponse(String agentResponse, 
                                                                         List<ActivityInfo> originalActivities) {
        log.debug("Parsing global activity allocation response: {}", agentResponse);
        
        GlobalActivityAllocationResponse response = new GlobalActivityAllocationResponse();
        response.setStatus("SUCCESS");
        response.setDailyPlans(new ArrayList<>());
        
        try {
            // 1. Try to extract complete JSON response
            GlobalActivityAllocationResponse jsonResponse = extractCompleteJsonResponse(agentResponse, originalActivities);
            if (jsonResponse != null && jsonResponse.getDailyPlans() != null && !jsonResponse.getDailyPlans().isEmpty()) {
                return jsonResponse;
            }
            
            // 2. Try to extract daily plans from partial JSON
            List<DailyActivityPlan> dailyPlans = extractDailyPlansFromPartialJson(agentResponse, originalActivities);
            if (!dailyPlans.isEmpty()) {
                response.setDailyPlans(dailyPlans);
                response.setAllocationReasoning("Parsed from partial AI response");
                return response;
            }
            
            // 3. Fallback: extract activities from text
            List<DailyActivityPlan> textPlans = extractDailyPlansFromText(agentResponse, originalActivities);
            if (!textPlans.isEmpty()) {
                response.setDailyPlans(textPlans);
                response.setStatus("PARTIAL");
                response.setAllocationReasoning("Extracted from text analysis");
                return response;
            }
            
            // 4. If all parsing fails
            log.warn("Unable to parse global activity allocation response");
            response.setStatus("ERROR");
            response.setErrorMessage("Unable to parse AI response for activity allocation");
            
        } catch (Exception e) {
            log.error("Error parsing global activity allocation response", e);
            response.setStatus("ERROR");
            response.setErrorMessage("Failed to parse AI response: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * Extract complete JSON response
     */
    private GlobalActivityAllocationResponse extractCompleteJsonResponse(String response, List<ActivityInfo> originalActivities) {
        try {
            // Extract JSON block
            Matcher jsonMatcher = JSON_PATTERN.matcher(response);
            if (jsonMatcher.find()) {
                String jsonContent = jsonMatcher.group(1);
                log.debug("Extracted JSON content: {}", jsonContent);
                
                JsonNode rootNode = objectMapper.readTree(jsonContent);
                
                GlobalActivityAllocationResponse result = new GlobalActivityAllocationResponse();
                result.setStatus(rootNode.has("status") ? rootNode.get("status").asText() : "SUCCESS");
                
                // Parse daily plans
                if (rootNode.has("dailyPlans")) {
                    List<DailyActivityPlan> dailyPlans = parseDailyPlansFromJsonNode(rootNode.get("dailyPlans"), originalActivities);
                    result.setDailyPlans(dailyPlans);
                }
                
                // Parse trip summary
                if (rootNode.has("tripSummary")) {
                    JsonNode summaryNode = rootNode.get("tripSummary");
                    GlobalActivityAllocationResponse.TripSummary summary = new GlobalActivityAllocationResponse.TripSummary();
                    
                    if (summaryNode.has("totalActivities")) {
                        summary.setTotalActivities(summaryNode.get("totalActivities").asInt());
                    }
                    if (summaryNode.has("totalDays")) {
                        summary.setTotalDays(summaryNode.get("totalDays").asInt());
                    }
                    if (summaryNode.has("mainThemes") && summaryNode.get("mainThemes").isArray()) {
                        List<String> themes = new ArrayList<>();
                        summaryNode.get("mainThemes").forEach(theme -> themes.add(theme.asText()));
                        summary.setMainThemes(themes);
                    }
                    if (summaryNode.has("intensityBalance")) {
                        summary.setIntensityBalance(summaryNode.get("intensityBalance").asText());
                    }
                    
                    result.setTripSummary(summary);
                }
                
                // Parse allocation reasoning
                if (rootNode.has("allocationReasoning")) {
                    result.setAllocationReasoning(rootNode.get("allocationReasoning").asText());
                }
                
                return result;
            }
        } catch (Exception e) {
            log.debug("Failed to extract complete JSON response", e);
        }
        
        return null;
    }
    
    /**
     * Parse daily plans from JSON node
     */
    private List<DailyActivityPlan> parseDailyPlansFromJsonNode(JsonNode dailyPlansNode, List<ActivityInfo> originalActivities) {
        List<DailyActivityPlan> dailyPlans = new ArrayList<>();
        
        if (dailyPlansNode.isArray()) {
            for (JsonNode planNode : dailyPlansNode) {
                DailyActivityPlan plan = parseSingleDailyPlan(planNode, originalActivities);
                if (plan != null) {
                    dailyPlans.add(plan);
                }
            }
        }
        
        return dailyPlans;
    }
    
    /**
     * Parse single daily plan from JSON node
     */
    private DailyActivityPlan parseSingleDailyPlan(JsonNode planNode, List<ActivityInfo> originalActivities) {
        try {
            DailyActivityPlan plan = new DailyActivityPlan();
            
            if (planNode.has("date")) {
                plan.setDate(LocalDate.parse(planNode.get("date").asText()));
            }
            if (planNode.has("cityCode")) {
                plan.setCityCode(planNode.get("cityCode").asText());
            }
            if (planNode.has("cityName")) {
                plan.setCityName(planNode.get("cityName").asText());
            }
            if (planNode.has("dayType")) {
                plan.setDayType(planNode.get("dayType").asText());
            }
            if (planNode.has("intensityLevel")) {
                plan.setIntensityLevel(planNode.get("intensityLevel").asText());
            }
            if (planNode.has("totalDuration")) {
                plan.setTotalDuration(planNode.get("totalDuration").asDouble());
            }
            if (planNode.has("startTime")) {
                plan.setStartTime(planNode.get("startTime").asText());
            }
            if (planNode.has("notes")) {
                plan.setNotes(planNode.get("notes").asText());
            }
            
            // Parse themes
            if (planNode.has("themes") && planNode.get("themes").isArray()) {
                List<String> themes = new ArrayList<>();
                planNode.get("themes").forEach(theme -> themes.add(theme.asText()));
                plan.setThemes(themes);
            }
            
            // Parse activities
            if (planNode.has("activities") && planNode.get("activities").isArray()) {
                List<ActivityInfo> activities = new ArrayList<>();
                for (JsonNode activityNode : planNode.get("activities")) {
                    ActivityInfo activity = matchActivityFromNode(activityNode, originalActivities);
                    if (activity != null) {
                        activities.add(activity);
                    }
                }
                plan.setActivities(activities);
            }
            
            return plan;
            
        } catch (Exception e) {
            log.debug("Failed to parse single daily plan", e);
            return null;
        }
    }
    
    /**
     * Match activity from JSON node
     */
    private ActivityInfo matchActivityFromNode(JsonNode activityNode, List<ActivityInfo> originalActivities) {
        try {
            // Try to match by activityId
            if (activityNode.has("activityId")) {
                String activityId = activityNode.get("activityId").asText();
                return findActivityById(activityId, originalActivities);
            }
            
            // Try to match by name
            if (activityNode.has("name")) {
                String name = activityNode.get("name").asText();
                return findActivityByName(name, originalActivities);
            }
            
        } catch (Exception e) {
            log.debug("Failed to match activity from node", e);
        }
        
        return null;
    }
    
    /**
     * Extract daily plans from partial JSON
     */
    private List<DailyActivityPlan> extractDailyPlansFromPartialJson(String response, List<ActivityInfo> originalActivities) {
        List<DailyActivityPlan> dailyPlans = new ArrayList<>();
        
        try {
            Matcher dailyPlansMatcher = DAILY_PLANS_PATTERN.matcher(response);
            if (dailyPlansMatcher.find()) {
                String dailyPlansContent = "[" + dailyPlansMatcher.group(1) + "]";
                JsonNode dailyPlansNode = objectMapper.readTree(dailyPlansContent);
                dailyPlans = parseDailyPlansFromJsonNode(dailyPlansNode, originalActivities);
            }
        } catch (Exception e) {
            log.debug("Failed to extract daily plans from partial JSON", e);
        }
        
        return dailyPlans;
    }
    
    /**
     * Extract daily plans from text analysis
     */
    private List<DailyActivityPlan> extractDailyPlansFromText(String response, List<ActivityInfo> originalActivities) {
        List<DailyActivityPlan> dailyPlans = new ArrayList<>();
        
        // This is a simplified text extraction - in practice, you might want more sophisticated parsing
        // For now, we'll create a basic fallback
        try {
            // Look for date patterns and activity mentions
            String[] lines = response.split("\n");
            DailyActivityPlan currentPlan = null;
            
            for (String line : lines) {
                line = line.trim();
                
                // Look for date patterns (YYYY-MM-DD)
                if (line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    if (currentPlan != null) {
                        dailyPlans.add(currentPlan);
                    }
                    currentPlan = new DailyActivityPlan();
                    currentPlan.setActivities(new ArrayList<>());
                    // Extract date if possible
                    Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
                    Matcher dateMatcher = datePattern.matcher(line);
                    if (dateMatcher.find()) {
                        currentPlan.setDate(LocalDate.parse(dateMatcher.group(1)));
                    }
                }
                
                // Look for activity names in the current plan
                if (currentPlan != null) {
                    for (ActivityInfo activity : originalActivities) {
                        if (line.toLowerCase().contains(activity.getName().toLowerCase())) {
                            if (!currentPlan.getActivities().contains(activity)) {
                                currentPlan.getActivities().add(activity);
                            }
                        }
                    }
                }
            }
            
            // Add the last plan
            if (currentPlan != null) {
                dailyPlans.add(currentPlan);
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract daily plans from text", e);
        }
        
        return dailyPlans;
    }
    
    /**
     * Find activity by ID
     */
    private ActivityInfo findActivityById(String id, List<ActivityInfo> activities) {
        return activities.stream()
            .filter(activity -> id.equals(activity.getActivityId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find activity by name
     */
    private ActivityInfo findActivityByName(String name, List<ActivityInfo> activities) {
        return activities.stream()
            .filter(activity -> activity.getName().equalsIgnoreCase(name) || 
                              activity.getName().toLowerCase().contains(name.toLowerCase()) ||
                              name.toLowerCase().contains(activity.getName().toLowerCase()))
            .findFirst()
            .orElse(null);
    }
}
