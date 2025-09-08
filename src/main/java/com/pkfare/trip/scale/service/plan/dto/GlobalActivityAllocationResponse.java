package com.pkfare.trip.scale.service.plan.dto;

import lombok.Data;

import java.util.List;

/**
 * Global Activity Allocation Response DTO
 * Contains the AI's recommended daily activity plans
 * 
 * @author Trip Scale Team
 */
@Data
public class GlobalActivityAllocationResponse {
    
    /**
     * Response status
     */
    private String status;
    
    /**
     * Daily activity plans for the entire trip
     */
    private List<DailyActivityPlan> dailyPlans;
    
    /**
     * Overall trip summary
     */
    private TripSummary tripSummary;
    
    /**
     * AI reasoning for the allocation
     */
    private String allocationReasoning;
    
    /**
     * Important notes for the entire trip
     */
    private String tripNotes;
    
    /**
     * Error message if allocation failed
     */
    private String errorMessage;
    
    /**
     * Trip summary information
     */
    @Data
    public static class TripSummary {
        private int totalActivities;
        private int totalDays;
        private List<String> mainThemes;
        private String intensityBalance; // e.g., "Well-balanced with alternating intensive and relaxed days"
        private Double estimatedTotalCost;
        private String costCurrency;
    }
}
