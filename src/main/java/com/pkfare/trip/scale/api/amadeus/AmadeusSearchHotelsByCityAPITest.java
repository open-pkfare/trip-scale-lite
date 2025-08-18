package com.pkfare.trip.scale.api.amadeus;

import com.amadeus.resources.Hotel;
import com.amadeus.resources.HotelOfferSearch;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusSearchHotelsByCityAPITest {

  public static void main(String[] args) {

    AmadeusSearchHotelsByCityAPI searchAPI = new AmadeusSearchHotelsByCityAPI();
    Hotel[] response = searchAPI.queryHotelByCity(buildQueryHotelByCityRequest());
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
