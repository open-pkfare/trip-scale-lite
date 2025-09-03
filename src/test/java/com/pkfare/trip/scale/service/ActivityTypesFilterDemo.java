package com.pkfare.trip.scale.service;

import com.amadeus.exceptions.ResponseException;

/**
 * Demonstration of filtering activities by type using the Amadeus Tours and Activities API
 */
public class ActivityTypesFilterDemo {
  // Replace with your actual Amadeus API credentials
   public static final String apiKey = "dfYr0PQs3GMwRFZTZzGlmR3lp6Gj6gjD";
   public static final String apiSecret = "fGS2iBdaPJpcG9SB";
    public static void main(String[] args) {


        try {
            AmadeusDestinationExperiencesWithSDK client = new AmadeusDestinationExperiencesWithSDK(apiKey, apiSecret);
            
            System.out.println("=== HISTORICAL AND CULTURAL ACTIVITIES IN PARIS ===");
            // Search for historical and cultural activities in Paris
            // Paris coordinates: 48.8566, 2.3522
            String historicalActivities = client.searchActivitiesAdvanced(
                48.8566, // Paris latitude
                2.3522,  // Paris longitude
                15,      // 15 km radius
                "EUR",   // Currency: Euro
                "SIGHTS" // Category group for historical/cultural attractions
            );
            System.out.println(historicalActivities);
            // Display the results
//            client.prettyPrintResults(historicalActivities);

//          other(client);

        } catch (ResponseException e) {
            System.out.println("Error accessing Amadeus API: " + e.getCode() + " - " + e.getDescription());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("General error occurred: ");
            e.printStackTrace();
        }
    }

  private static void other(AmadeusDestinationExperiencesWithSDK client) throws ResponseException {
    System.out.println("\n=== OUTDOOR ACTIVITIES IN PARIS ===");
    // Search for outdoor/natural activities in Paris
    String outdoorActivities = client.searchActivitiesAdvanced(
        48.8566, // Paris latitude
        2.3522,  // Paris longitude
        15,      // 15 km radius
        "EUR",   // Currency: Euro
        "ACTIVITIES" // Category group for activities/outdoor experiences
    );

    // Display the results
    client.prettyPrintResults(outdoorActivities);

    System.out.println("\n=== NIGHTLIFE ACTIVITIES IN PARIS ===");
    // Search for nightlife activities in Paris
    String nightlifeActivities = client.searchActivitiesAdvanced(
        48.8566, // Paris latitude
        2.3522,  // Paris longitude
        15,      // 15 km radius
        "EUR",   // Currency: Euro
        "NIGHTLIFE" // Category group for nightlife experiences
    );

    // Display the results
    client.prettyPrintResults(nightlifeActivities);
  }
}
