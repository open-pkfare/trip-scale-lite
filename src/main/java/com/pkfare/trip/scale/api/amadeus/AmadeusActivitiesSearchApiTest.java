package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Activity;
import com.pkfare.trip.scale.api.amadeus.activities.AmadeusActivitiesSearchApi;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusActivitiesSearchApiTest {

  @Configuration
  @ComponentScan(basePackages = {"com.pkfare.trip.scale.api.amadeus", "com.pkfare.trip.scale.cache"})
  static class TestConfig {
  }

  public static void main(String[] args) throws ResponseException {
    // 测试/v1/shopping/activities
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
      AmadeusActivitiesSearchApi searchAPI = context.getBean(AmadeusActivitiesSearchApi.class);
      List<ActivityDto> response = searchAPI.searchActivities(buildActivitiesSearchRequest());
      log.info("response : {}",response);
    }
  }

  private static ActivitiesSearchRequest buildActivitiesSearchRequest() {
    ActivitiesSearchRequest request = new ActivitiesSearchRequest();
    request.setLatitude(41.8967068);
    request.setLongitude(12.4822025);
    request.setRadius(10);
    return request;
  }

}
