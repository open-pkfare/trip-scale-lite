package com.pkfare.trip.scale.api.amadeus.activities.response;

import com.amadeus.resources.Activity.GeoCode;
import java.util.List;
import lombok.Data;

@Data
public class ActivityDto {
  private String type;
  private String id;
  private String name;
  private String shortDescription;
  private String description;
  private GeoCodeDto geoCode;
  private String rating;
  private String bookingLink;
  private String minimumDuration;
  private ElementaryPriceDto price;
  private List<String> pictures;
}
