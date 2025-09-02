package com.pkfare.trip.scale.api.amadeus.hoteloffers;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.resources.Hotel;
import com.amadeus.resources.HotelOfferSearch;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusClient;
import com.pkfare.trip.scale.api.amadeus.exception.AmadeusApiException;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AmadeusHotelOffersSearchAPI {

  public HotelOfferSearch[] hotelOffersSearch(HotelOffersSearchRequest hotelOffersSearchRequest) {
    Amadeus amadeus = AmadeusClient.get();
    Params params = Params.with("hotelIds", hotelOffersSearchRequest.getHotelIds())
        .and("adults", hotelOffersSearchRequest.getAdults())
        .and("checkInDate", hotelOffersSearchRequest.getCheckInDate()).and("checkOutDate", hotelOffersSearchRequest.getCheckOutDate())
        .and("countryOfResidence", hotelOffersSearchRequest.getCountryOfResidence()).and("roomQuantity", hotelOffersSearchRequest.getRoomQuantity())
        .and("priceRange", hotelOffersSearchRequest.getPriceRange()).and("currency", hotelOffersSearchRequest.getCurrency())
        .and("bestRateOnly", hotelOffersSearchRequest.getBestRateOnly()).and("paymentPolicy", hotelOffersSearchRequest.getPaymentPolicy());

    try {
      HotelOfferSearch[] hotelOffers = amadeus.shopping.hotelOffersSearch.get(params);
      if (hotelOffers == null || hotelOffers.length == 0) {
        log.error("call AmadeusHotelOffersSearchAPI return empty，resonse:{} ", hotelOffers);
        return hotelOffers;
      }
      if (hotelOffers[0].getResponse().getStatusCode() != 200) {
        log.error("call AmadeusHotelOffersSearchAPI failed，resonse：{}", hotelOffers[0].getResponse());
        throw new AmadeusApiException(hotelOffers[0].getResponse().getStatusCode(), hotelOffers[0].getResponse().getResult().toString());
      }
      return hotelOffers;
    } catch (Exception e) {
      log.error("call AmadeusHotelOffersSearchAPI failed", e);
      throw new AmadeusApiException(500, "call AmadeusHotelOffersSearchAPI failed");
    }

  }

}
