package com.pkfare.trip.scale.api.amadeus.hotelbycity.request;

import com.google.common.collect.Lists;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class QueryHotelByGeocodeRequest {

  /**
   * Latitude of the geographical coordinates.
   */
  private double latitude ;
  /**
   * Longitude of the geographical coordinates.
   */
  private double longitude  ;
  /**
   * Maximum distance from the geographical coordinates express in defined units. The default unit is metric kilometer.
   */
  private Integer radius;

  /**
   * Unit of measurement used to express the radius. It can be either metric kilometer or imperial mile. Available values : KM, MILE
   */
  private String radiusUnit;

  /**
   * Array of hotel chain codes. Each code is a string consisted of 2 capital alphabetic characters.
   */
  private List<String> chainCodes ;
  /**
   * List of amenities.
   */
  private List<String> amenities ;

  /**
   * Hotel stars. Up to four values can be requested at the same time in a comma separated list.
   */
  private List<String> ratings = Lists.newArrayList("1","2","3","4","5");
  /**
   * Hotel source with values BEDBANK for aggregators, DIRECTCHAIN for GDS/Distribution and ALL for both.
   * Available values : BEDBANK, DIRECTCHAIN, ALL
   * Default value : ALL
   */
  private String hotelSource = "ALL" ;
  /**
   * City code. mock use
   */
  private String cityCode;
}
