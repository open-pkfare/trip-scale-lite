package com.pkfare.trip.scale.api.amadeus.config;

import com.amadeus.Amadeus;
import java.util.HashMap;
import java.util.Map;

public class AmadeusClient {

  private static Amadeus amadeus;

  static {
    Map<String, String> configMap = new HashMap<>();
    configMap.put("AMADEUS_CLIENT_ID",AmadeusAuthenticateConfig.getClientKey());
    configMap.put("AMADEUS_CLIENT_SECRET",AmadeusAuthenticateConfig.getClientSecret());
    amadeus = Amadeus.builder(configMap).build();
  }

  public static Amadeus get(){
    return amadeus;
  }

}
