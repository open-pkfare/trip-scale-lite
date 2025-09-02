package com.pkfare.trip.scale.api.amadeus.activities;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Activity;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusActivitiesSearchApi {

  public List<ActivityDto> searchActivities(ActivitiesSearchRequest activitiesSearchRequest) {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("latitude", activitiesSearchRequest.getLatitude())
        .and("longitude", activitiesSearchRequest.getLongitude())
        .and("radius", activitiesSearchRequest.getRadius());

    try {
      Activity[] activities = amadeus.shopping.activities.get(params);
      if (activities == null || activities.length == 0) {
        log.error("call AmadeusActivitiesSearchApi return empty，resonse:{} ", activities);
        return Lists.newArrayList();
      }
      if (activities[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusActivitiesSearchApi failed，resonse：{}", activities[0].getResponse());
        throw new AmadeusApiException(activities[0].getResponse().getStatusCode(), activities[0].getResponse().getResult().toString());
      }
      return convert2Dtos(activities);
    } catch (Exception e) {
      log.error("call AmadeusActivitiesSearchApi failed", e);
      throw new AmadeusApiException(500, "call AmadeusActivitiesSearchApi failed");
    }

  }

  private List<ActivityDto> convert2Dtos(Activity[] activities) {
    return Lists.newArrayList();
  }


}
