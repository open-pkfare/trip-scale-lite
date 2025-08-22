package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Activity;
import com.pkfare.trip.scale.api.amadeus.activities.AmadeusActivitiesSearchApi;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusActivitiesSearchApiTest {

  public static void main(String[] args) throws ResponseException {
    // 测试/v3/shopping/hotel-offers
    AmadeusActivitiesSearchApi searchAPI = new AmadeusActivitiesSearchApi();
    Activity[] response = searchAPI.searchActivities(buildActivitiesSearchRequest());
    log.info("response : {}",response);

  }

  private static ActivitiesSearchRequest buildActivitiesSearchRequest() {
    ActivitiesSearchRequest request = new ActivitiesSearchRequest();
    request.setLatitude(41.397158);
    request.setLongitude(2.160873);
    request.setRadius(10);
    return request;
  }

}
