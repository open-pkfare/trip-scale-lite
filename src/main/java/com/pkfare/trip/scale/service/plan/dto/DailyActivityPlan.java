package com.pkfare.trip.scale.service.plan.dto;

import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily Activity Plan DTO
 * Represents a day's activity plan with activities and metadata
 * 
 * @author Trip Scale Team
 */
@Data
public class DailyActivityPlan {
    
    /**
     * Date of the plan
     */
    private LocalDate date;
    
    /**
     * City code
     */
    private String cityCode;
    
    /**
     * City name
     */
    private String cityName;
    
    /**
     * Day type (arrival_day, departure_day, full_day)
     */
    private String dayType;
    
    /**
     * Recommended activities for this day
     */
    private List<ActivityInfo> activities;
    
    /**
     * Activity intensity level (relaxed, moderate, intensive)
     */
    private String intensityLevel;
    
    /**
     * Estimated total duration in hours
     */
    private Double totalDuration;
    
    /**
     * Recommended start time
     */
    private String startTime;
    
    /**
     * Special notes for this day
     */
    private String notes;
    
    /**
     * Activity themes for this day (e.g., "cultural", "outdoor", "food")
     */
    private List<String> themes;
}
