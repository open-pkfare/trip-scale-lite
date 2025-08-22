package com.pkfare.trip.scale.dto;

import com.google.maps.model.EncodedPolyline;
import java.util.List;
import lombok.Data;

@Data
public class DestinationTourPath {

  EncodedPolyline polyline;
  List<GeoLocation> orderedLocations;

}
