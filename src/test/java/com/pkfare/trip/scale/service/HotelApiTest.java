package com.pkfare.trip.scale.service;

import com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByGeocodeRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelInfoDto;
import com.pkfare.trip.scale.util.JsonUtil;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HotelApiTest {

  @Autowired
  private AmadeusSearchHotelsByCityAPI amadeusSearchHotelsByCityAPI;

  @Test
  public void testqueryHotelByGeocode() {
    QueryHotelByGeocodeRequest request = new QueryHotelByGeocodeRequest();
    request.setLatitude(40.7128);
    request.setLongitude(-74.0060);
    request.setRadius(50);
    request.setRadiusUnit("KM");
    request.setAmenities(Arrays.asList("wifi"));
    request.setRatings(Arrays.asList("1", "2", "3"));
    List<HotelInfoDto> hotelInfoDtos = amadeusSearchHotelsByCityAPI.queryHotelByGeocode(request);
    System.out.println(JsonUtil.toJson(hotelInfoDtos.getFirst()));
  }

}
