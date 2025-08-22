package com.pkfare.trip.scale.api.google;

import com.google.api.client.util.Lists;
import com.google.gson.Gson;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.TravelMode;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.dto.DestinationTourPath;
import com.pkfare.trip.scale.dto.GeoLocation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleMapService {

  private static GeoApiContext CONTEXT = new GeoApiContext.Builder()
      .apiKey(GoogleConfig.GOOGLE_API_KEY)
      .build();

  public LatLng getLatLng(String address) {
    try {
      GeocodingResult[] geocodingResults = GeocodingApi.geocode(CONTEXT, address).await();
      if (ArrayUtils.isNotEmpty(geocodingResults)) {
        GeocodingResult geocodingResult = geocodingResults[0];
        return geocodingResult.geometry.location;
      }else {
        throw new RuntimeException("Geocoding search empty, check the address");
      }
    } catch (Exception e) {
      throw new RuntimeException("Geocoding search failed", e);
    }
  }

  public Optional<DestinationTourPath> optimizeRoute(List<GeoLocation> locations) {
    if (locations == null || locations.size() < 2) {
      throw new IllegalArgumentException("At least 2 locations required");
    }
    
    try {
      LatLng origin = locations.get(0).getLatLng();
      LatLng destination = locations.get(locations.size() - 1).getLatLng();
      List<LatLng> pass = locations.subList(1, locations.size() - 1).stream().map(GeoLocation::getLatLng).toList();
      LatLng[] waypoints = pass.toArray(new LatLng[0]);
      DestinationTourPath destinationTourPath = null;
      DirectionsResult directionsResult = DirectionsApi.newRequest(CONTEXT)
          .origin(origin)
          .destination(destination)
          .waypoints(waypoints)
          .optimizeWaypoints(true)
          .mode(TravelMode.WALKING)
          .await();
      if (ArrayUtils.isNotEmpty(directionsResult.routes)){
        int[] order = directionsResult.routes[0].waypointOrder;
        List<GeoLocation> orderedLocations = Lists.newArrayList();
        for (int i : order) {
          orderedLocations.add(locations.get(i));
        }
        destinationTourPath = new DestinationTourPath();
        destinationTourPath.setOrderedLocations(orderedLocations);
        destinationTourPath.setPolyline(directionsResult.routes[0].overviewPolyline);
      }
      return Optional.ofNullable(destinationTourPath);
    } catch (Exception e) {
      throw new RuntimeException("Route optimization failed", e);
    }
  }

  public static void main(String[] args) {
    GoogleMapService service = new GoogleMapService();
    LatLng latLng = service.getLatLng("Florence, Italy");

    try {
      //市中心半径5km内的景点
      PlacesSearchResponse p = PlacesApi.textSearchQuery(CONTEXT, PlaceType.TOURIST_ATTRACTION).location(latLng).radius(5000).await();

      List<GeoLocation> geoLocations = Arrays.stream(p.results).map(r-> new GeoLocation(r.name,r.geometry.location)).toList();
      List<GeoLocation> copy = Lists.newArrayList(geoLocations);

      //景点路线规划
      Optional<DestinationTourPath> directionsResult = service.optimizeRoute(copy);
      System.out.println(new Gson().toJson(directionsResult));
    }catch (Throwable e){
      log.error("", e);
    }


  }
}
