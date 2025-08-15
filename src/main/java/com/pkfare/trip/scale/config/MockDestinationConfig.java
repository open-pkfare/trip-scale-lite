package com.pkfare.trip.scale.config;

import com.pkfare.trip.scale.dto.DestinationSuggestion;
import java.util.List;
import lombok.Data;

/**
 * Configuration class for mock destination data.
 */
@Data
public class MockDestinationConfig {
    
    private List<DestinationSuggestion> suggestions;

    public static List<DestinationSuggestion> getSuggestions(){
        return List.of(
            new DestinationSuggestion("Vietnam, Hanoi", "Perfect blend of culture and cuisine with affordable prices", 0.85),
            new DestinationSuggestion("Japan, Kyoto", "Rich cultural heritage and beautiful temples", 0.92),
            new DestinationSuggestion("Thailand, Chiang Mai", "Authentic local experiences and great food scene", 0.78),
            new DestinationSuggestion("Portugal, Porto", "Charming architecture and excellent wine culture", 0.81),
            new DestinationSuggestion("South Korea, Busan", "Coastal beauty with modern city amenities", 0.76),
            new DestinationSuggestion("Taiwan, Taipei", "Night markets and incredible street food", 0.83),
            new DestinationSuggestion("Greece, Santorini", "Stunning sunsets and Mediterranean charm", 0.89),
            new DestinationSuggestion("Morocco, Marrakech", "Exotic culture and vibrant markets", 0.74),
            new DestinationSuggestion("Nepal, Kathmandu", "Gateway to Himalayas with rich spiritual culture", 0.72),
            new DestinationSuggestion("Peru, Cusco", "Ancient Incan history and gateway to Machu Picchu", 0.87)
        );
    }
}