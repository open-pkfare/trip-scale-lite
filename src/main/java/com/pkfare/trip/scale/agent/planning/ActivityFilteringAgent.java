package com.pkfare.trip.scale.agent.planning;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.pkfare.trip.scale.assistance.PersonalPreferenceService;
import com.pkfare.trip.scale.config.GoogleConfig;

/**
 * 活动筛选Agent - 基于Google ADK大模型
 * 根据航班时间段和用户偏好对活动进行二次筛选
 * 
 * @author Trip Scale Team
 */
public class ActivityFilteringAgent {

    private static final String NAME = "activity_filtering_agent";
    
    private static BaseAgent INSTANCE;

    public static BaseAgent instance() {
        if (null == INSTANCE) {
            INSTANCE = LlmAgent.builder()
                    .name(NAME)
                    .model(GoogleConfig.GEMINI_2_5_FLASH)
                    .description("Agent to filter and recommend activities based on flight schedules and user preferences.")
                    .instruction(ActivityFilteringPrompt.ACTIVITY_FILTERING_PROMPT)
                    .tools(
                            FunctionTool.create(PersonalPreferenceService.class, "preferences"),
                            FunctionTool.create(PersonalPreferenceService.class, "recentFocusAndHistoricalTrip")
                    )
                    .build();
        }
        return INSTANCE;
    }
}
