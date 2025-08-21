package com.pkfare.trip.scale.dto;

import com.google.maps.model.LatLng;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class GeoLocation {

  private String key;
  private LatLng latLng;

}
