package com.pkfare.trip.scale.api.amadeus.hotelbycity.request;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.Data;

@Data
public class QueryHotelByCityRequest {

  /**
   * Destination city code or airport code. In case of city code , the search will be done around the city center. Available codes can be found in IATA table codes (3 chars IATA Code).
   */
  private String cityCode;
  /**
   * Maximum distance from the geographical coordinates express in defined units. The default unit is metric kilometer.
   */
  private Integer radius;

  /**
   * Unit of measurement used to express the radius. It can be either metric kilometer or imperial mile.
   */
  private String radiusUnit;

  /**
   * List of amenities.
   */
  private List<String> amenities ;

  /**
   * Hotel stars. Up to four values can be requested at the same time in a comma separated list.
   */
  private List<String> ratings = Lists.newArrayList("1","2","3","4","5");


}
