package com.pkfare.trip.scale.service.plan.dto;

import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Global Activity Allocation Request DTO
 * Contains all information needed for AI to allocate activities across all days
 * 
 * @author Trip Scale Team
 */
@Data
public class GlobalActivityAllocationRequest {
    
    /**
     * All available activities across all cities
     */
    private List<ActivityInfo> allActivities;
    
    /**
     * Trip itinerary information
     */
    private TripItinerary itinerary;
    
    /**
     * User preferences
     */
    private ActivityFilteringRequest.UserPreferences userPreferences;
    
    /**
     * Flight time constraints
     */
    private FlightConstraints flightConstraints;
    
    /**
     * Budget information
     */
    private String budget;
    
    /**
     * Currency
     */
    private String currency;
    
    /**
     * Trip itinerary details
     */
    @Data
    public static class TripItinerary {
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalDays;
        private List<CityStay> cityStays;
    }
    
    /**
     * City stay information
     */
    @Data
    public static class CityStay {
        private String cityCode;
        private String cityName;
        private LocalDate startDate;
        private LocalDate endDate;
        private int stayDays;
        private String reasonForRecommendation;
    }
    
    /**
     * Flight time constraints
     */
    @Data
    public static class FlightConstraints {
        private LocalDate arrivalDate;
        private String arrivalTime;
        private LocalDate departureDate;
        private String departureTime;
        private Map<LocalDate, String> dayTypes; // arrival_day, departure_day, full_day
    }
}
