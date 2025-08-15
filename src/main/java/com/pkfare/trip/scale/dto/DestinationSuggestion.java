package com.pkfare.trip.scale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a destination suggestion from AI
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationSuggestion {
    
    @NotBlank(message = "Destination is required")
    private String destination;
    
    private String reason;
    
    @DecimalMin(value = "0.0", message = "Confidence must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence must be at most 1.0")
    private Double confidence;
}