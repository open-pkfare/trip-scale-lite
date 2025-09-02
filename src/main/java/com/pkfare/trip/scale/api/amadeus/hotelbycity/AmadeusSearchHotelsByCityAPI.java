package com.pkfare.trip.scale.api.amadeus.hotelbycity;


import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Hotel;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByGeocodeRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmadeusSearchHotelsByCityAPI {

  public Hotel[] queryHotelByCity(QueryHotelByCityRequest queryHotelByCityRequest) throws AmadeusApiException {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("cityCode", queryHotelByCityRequest.getCityCode())
        .and("radius", queryHotelByCityRequest.getRadius())
        .and("radiusUnit", queryHotelByCityRequest.getRadiusUnit()).and("ratings", queryHotelByCityRequest.getRatings());

    try {
      Hotel[] hotels = amadeus.referenceData.locations.hotels.byCity.get(params);
      if (hotels == null || hotels.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotels);
        return hotels;
      }
      if (hotels[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotels[0].getResponse());
        throw new AmadeusApiException(hotels[0].getResponse().getStatusCode(), hotels[0].getResponse().getResult().toString());
      }
      return hotels;
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }

  }

  public Hotel[] queryHotelByGeocode(QueryHotelByGeocodeRequest request) throws AmadeusApiException {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("latitude", request.getLatitude())
        .and("longitude", request.getLongitude())
        .and("radius", request.getRadius())
        .and("radiusUnit", request.getRadiusUnit())
        .and("ratings", request.getRatings())
        .and("amenities", request.getAmenities());

    try {
      Hotel[] hotels = amadeus.referenceData.locations.hotels.byGeocode.get(params);
      if (hotels == null || hotels.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotels);
        return hotels;
      }
      if (hotels[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotels[0].getResponse());
        throw new AmadeusApiException(hotels[0].getResponse().getStatusCode(), hotels[0].getResponse().getResult().toString());
      }
      return hotels;
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }
  }

}
