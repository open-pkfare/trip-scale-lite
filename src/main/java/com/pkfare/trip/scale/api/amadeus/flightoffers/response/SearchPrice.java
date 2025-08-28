package com.pkfare.trip.scale.api.amadeus.flightoffers.response;

import com.amadeus.resources.FlightOfferSearch.AdditionalService;
import com.amadeus.resources.FlightOfferSearch.Fee;
import com.amadeus.resources.FlightOfferSearch.Tax;
import lombok.Data;


@Data
public class SearchPrice {

  private String currency;
  private String total;
  //private String base;
  // private Fee[] fees;
  //private String grandTotal;
  // private Tax[] taxes;
  //private String refundableTaxes;
  //private String margin;
  //private String billingCurrency;
  // private AdditionalService[] additionalServices;

}
