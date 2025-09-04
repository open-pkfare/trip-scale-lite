package com.pkfare.trip.scale.service;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Java implementation of Amadeus Tours and Activities API client using the official Amadeus Java SDK
 * This API helps search for destination activities, tours and attractions
 */
public class AmadeusDestinationExperiencesWithSDK {
    
    private final Amadeus amadeus;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ACTIVITIES_ENDPOINT = "/v1/shopping/activities";
    private static final String ACTIVITY_ENDPOINT = "/v1/shopping/activities/%s";
    
    /**
     * Constructor with API credentials
     */
    public AmadeusDestinationExperiencesWithSDK(String apiKey, String apiSecret) {
        // Initialize the Amadeus SDK with your credentials
        amadeus = Amadeus.builder(apiKey, apiSecret)
                .setHostname("test") // Use test environment, use .build() for production
                .build();
    }
    
    /**
     * Search for activities by location
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return JSON response as a string
     * @throws ResponseException If there's an error in the API response
     */
    public String searchActivitiesByGeocode(double latitude, double longitude) throws ResponseException {
        // Use the SDK to make the API call
        return amadeus.get(ACTIVITIES_ENDPOINT,
                Params.with("latitude", Double.toString(latitude))
                      .and("longitude", Double.toString(longitude))).getBody();
    }
    
    /**
     * Search for activities with advanced parameters including activity type filtering
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param radius Search radius in kilometers
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param categoryGroup Type of activities to filter (e.g., "SIGHTS", "ACTIVITIES", "NIGHTLIFE", etc.)
     * @return JSON response as a string
     * @throws ResponseException If there's an error in the API response
     */
    public String searchActivitiesAdvanced(
            double latitude, 
            double longitude, 
            Integer radius, 
            String currency,
            String categoryGroup) throws ResponseException {
        
        // Create parameter builder
        Params params = Params.with("latitude", Double.toString(latitude))
                              .and("longitude", Double.toString(longitude));
        
        // Add optional parameters if provided
        if (radius != null) {
            params.and("radius", radius.toString());
        }
        if (currency != null) {
            params.and("currency", currency);
        }
        if (categoryGroup != null) {
            params.and("categoryGroup", categoryGroup);
        }
        
        // Use the SDK to make the API call
        try {
            return amadeus.get(ACTIVITIES_ENDPOINT, params).getBody();
        } catch (ResponseException e) {
            System.out.println("API Error: " + e.getCode() + " - " + e.getDescription());
            System.out.println("Request to: " + ACTIVITIES_ENDPOINT);
            System.out.println("Request Parameters: " + params.toString());
            throw e; // Re-throw the exception after logging
        }
    }
    
    /**
     * Get details for a specific activity
     * 
     * @param activityId ID of the activity
     * @return JSON response as a string
     * @throws ResponseException If there's an error in the API response
     */
    public String getActivityDetails(String activityId) throws ResponseException {
        String endpoint = String.format(ACTIVITY_ENDPOINT, activityId);
        
        try {
            return amadeus.get(endpoint).getBody();
        } catch (ResponseException e) {
            System.out.println("API Error: " + e.getCode() + " - " + e.getDescription());
            System.out.println("Request to: " + endpoint);
            throw e; // Re-throw the exception after logging
        }
    }
    
    /**
     * Pretty print the activities search results
     * 
     * @param jsonResponse JSON response from the API
     */
    public void prettyPrintResults(String jsonResponse) {
        try {
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            System.out.println("Activities Search Results:");
            System.out.println("===========================");
            
            if (rootNode.has("data") && rootNode.get("data").isArray()) {
                JsonNode dataArray = rootNode.get("data");
                
                for (int i = 0; i < dataArray.size(); i++) {
                    JsonNode activity = dataArray.get(i);
                    
                    // Extract and display activity information
                    String id = activity.has("id") ? activity.get("id").asText() : "N/A";
                    String name = activity.has("name") ? activity.get("name").asText() : "Unnamed Activity";
                    
                    System.out.println("Activity #" + (i + 1));
                    System.out.println("ID: " + id);
                    System.out.println("Name: " + name);
                    
                    // Extract price information if available
                    if (activity.has("price")) {
                        JsonNode price = activity.get("price");
                        String amount = price.has("amount") ? price.get("amount").asText() : "N/A";
                        String currencyCode = price.has("currencyCode") ? price.get("currencyCode").asText() : "EUR";
                        System.out.println("Price: " + amount + " " + currencyCode);
                    }
                    
                    // Extract and display short description if available
                    if (activity.has("shortDescription")) {
                        String shortDescription = activity.get("shortDescription").asText();
                        if (shortDescription.length() > 100) {
                            shortDescription = shortDescription.substring(0, 97) + "...";
                        }
                        System.out.println("Description: " + shortDescription);
                    }
                    
                    // Extract and display category information if available
                    if (activity.has("categoryGroups") && activity.get("categoryGroups").isArray()) {
                        JsonNode categories = activity.get("categoryGroups");
                        System.out.print("Categories: ");
                        for (int j = 0; j < categories.size(); j++) {
                            System.out.print(categories.get(j).asText());
                            if (j < categories.size() - 1) {
                                System.out.print(", ");
                            }
                        }
                        System.out.println();
                    }
                    
                    System.out.println("---------------------------");
                }
            } else {
                System.out.println("No activities found in the response.");
                System.out.println("Raw response: " + jsonResponse);
            }
            
        } catch (Exception e) {
            System.out.println("Error parsing or printing results: " + e.getMessage());
            System.out.println("Raw response: " + jsonResponse);
        }
    }
    
    /**
     * Main method for demonstration
     */
    public static void main(String[] args) {
        // Replace with your actual API credentials
        String apiKey = "yourapikey";
        String apiSecret = "yourapisecret";
        
        try {
            AmadeusDestinationExperiencesWithSDK searcher = new AmadeusDestinationExperiencesWithSDK(apiKey, apiSecret);
            
            // Search for activities in Barcelona (41.397158, 2.160873)
            System.out.println("Performing activities search in Barcelona...");
            String result = searcher.searchActivitiesAdvanced(
                41.397158,    // Barcelona latitude
                2.160873,     // Barcelona longitude
                20,           // 20 km radius
                "EUR",        // Currency
                "SIGHTS"      // Filter by SIGHTS category
            );
            searcher.prettyPrintResults(result);
            
        } catch (Exception e) {
            System.out.println("Error occurred: ");
            e.printStackTrace();
        }
    }
}
