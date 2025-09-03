package com.pkfare.trip.scale.api.amadeus;

import com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.response.HotelInfoDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusSearchHotelsByCityAPITest {

  public static void main(String[] args) {
    // 测试
    AmadeusSearchHotelsByCityAPI searchAPI = new AmadeusSearchHotelsByCityAPI();
    List<HotelInfoDto> response = searchAPI.queryHotelByCity(buildQueryHotelByCityRequest());
    log.info("response : {}",response);

  }

  private static QueryHotelByCityRequest buildQueryHotelByCityRequest() {
    QueryHotelByCityRequest request = new QueryHotelByCityRequest();
    request.setCityCode("PAR");
    request.setRadius(5);
    request.setRadiusUnit("KM");

    return request;
  }

}
