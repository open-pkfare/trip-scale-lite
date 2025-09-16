package com.pkfare.trip.scale.agent.planning;

/**
 * 旅行计划生成提示词
 * 
 * @author Trip Scale Team
 */
public class PlanningPrompt {
    
    public static final String PLANNING_PROMPT = "###background\n"
        + "you are a trip planning assistant, conclude and organize a purchasable trip plan routing for user refer to historical dialogs.\n"
        + "\n"
        + "### ATTENTION\n"
        + "1. country_code follow ISO3166-1 standard with 2 letters.\n"
        + "2. If the city has airport, location_code is required, or let it be null, it follows IATA standard with 3 letters.\n"
        + "3. Ensure the overall order of travel destinations is logically arranged based on objective geographical locations.\n"
        + "\n"
        + "### TODO\n"
        + "1. Extract relevant data from previous dialogs, don't miss every key factors. then strickly follow OUTPUT command."
        + "2. Do not add any additional factors which did not mentioned in previous dialogs."
        + "3. Fill location_code if the location has code."
        + "\n"
        + "### OUTPUT "
        + "output strictly follow the array data constructure, 6 hyphens is mandatory:\n"
        + "\n"
        + "------[{\n"
        + "\"stay_days\":int,\n"
        + "\"destination_city\":String,\n"
        + "\"country_code\":String,\n"
        + "\"location_code\",String,\n"
        + "\"reason_for_recommendation\":String\n"
        + "}\n"
        + "]\"";
    
    /**
     * 构建具体的提示词
     * 
     * @param planInfo 计划信息JSON字符串
     * @return 完整的提示词
     */
    public static String buildPrompt(String planInfo) {
        return PLANNING_PROMPT + "\n\n旅行信息：\n" + planInfo + 
               "\n\n请基于以上信息生成详细的旅行计划。";
    }
}