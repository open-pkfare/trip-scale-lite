package com.pkfare.trip.scale.plan.service.impl;

import com.amadeus.resources.FlightOfferSearch;
import com.amadeus.resources.FlightOfferSearch.Itinerary;
import com.amadeus.resources.FlightOfferSearch.SearchSegment;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.AdjustFlightParam;
import com.pkfare.trip.scale.plan.service.param.FlightAdjustTypeEnum;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.ItineraryInfo;
import com.pkfare.trip.scale.plan.service.response.SegmentInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusFlightService;
import com.pkfare.trip.scale.service.plan.FlightSearchService;
import com.pkfare.trip.scale.util.PriceUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 航班调整服务实现类
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class FlightAdjustServiceImpl implements TripPlanAdjustInterface {
  private static final Gson gson = new Gson();
  @Autowired
  private FlightSearchService flightSearchService;
  @Autowired
  private AmadeusFlightService amadeusFlightService;

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripPlan tripPlan,  JsonObject adjustParam) {
    AdjustFlightParam adjustFlightParam = gson.fromJson(adjustParam, AdjustFlightParam.class);
    log.info("Adjusting flight param: {}", adjustFlightParam);
    List<FlightInfo> flights = tripPlan.getFlights();
    boolean found = false;
    for (int i = 0; i < flights.size(); i++) {
      FlightInfo flight = flights.get(i);
      if (flight.getId().equals(adjustFlightParam.getId())) {
        FlightInfo newFlight = searchFlightInfo(generatePlanParam, flight, adjustFlightParam);
        flights.set(i, newFlight);
        found = true;
        break;
      }
    }
    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_FLIGHT_FOUND);
    }
  }

  public FlightInfo searchFlightInfo(GeneratePlanParam planParam, FlightInfo oldFlightInfo, AdjustFlightParam adjustPlanParam) {
    List<ItineraryInfo> itineraries = oldFlightInfo.getItineraries();
    String departure = itineraries.getFirst().getDeparture();
    String arrival = itineraries.getLast().getArrival();
    String departureTime = itineraries.getFirst().getSegments().getFirst().getDepartureTime();
    List<SegmentInfo> segments = itineraries.getLast().getSegments();
    String arrivalTime = segments.getLast().getArrivalTime();
    FlightAdjustTypeEnum adjustTypeEnum = FlightAdjustTypeEnum.getByCode(adjustPlanParam.getAdjustType()).orElse(FlightAdjustTypeEnum.REPLACE);

    FlightOffersSearchRequest request = new FlightOffersSearchRequest();
    request.setOrigin(departure);
    request.setDestination(arrival);
    request.setAdults(planParam.getAdult_number());
    request.setChildren(planParam.getChild_number());
    request.setInfants(0);
    request.setNonStop(adjustPlanParam.isNoStop());
    request.setCurrency(planParam.getCurrency());
    request.setMaxPrice(new BigDecimal(oldFlightInfo.getTotal()).intValue());
    request.setMax(50);
    if (adjustTypeEnum.equals(FlightAdjustTypeEnum.CHEAPER)) {
      request.setTravelClass("PREMIUM_ECONOMY");
      request.setMaxPrice(new BigDecimal(oldFlightInfo.getTotal()).multiply(new BigDecimal("0.8")).intValue());
    }

    FlightOfferSearch[] offers = amadeusFlightService.searchFlightOffers(request);
    if (offers == null || offers.length == 0) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_FLIGHT_FOUND);
    }
    // 按价格升序排序
    List<FlightOfferSearch> offerSearches = Arrays.asList(offers);
    offerSearches.sort((o1, o2) -> {
      BigDecimal price1 = PriceUtil.parsePrice(o1.getPrice().getTotal());
      BigDecimal price2 = PriceUtil.parsePrice(o2.getPrice().getTotal());
      return price1.compareTo(price2);
    });

    // 根据adjustTypeEnum不同，从offers找出不同的航班
    FlightOfferSearch target = offerSearches.getFirst();
    if (adjustTypeEnum.equals(FlightAdjustTypeEnum.REPLACE)
        || adjustTypeEnum.equals(FlightAdjustTypeEnum.CHEAPER)) {
      // 从offers找出与flightInfo不同航班号的一个航班
      Set<String> oldSegmentSet = oldFlightInfo.getItineraries().stream().flatMap(itinerary -> itinerary.getSegments().stream())
          .map(segment -> segment.getCarrierCode().concat(segment.getNumber()))
          .collect(Collectors.toSet());
      Optional<FlightOfferSearch> optional = offerSearches.stream()
          .filter(offer -> {
            return Arrays.stream(offer.getItineraries()).flatMap(itinerary -> Arrays.stream(itinerary.getSegments()))
                .anyMatch(segment -> !oldSegmentSet.contains(segment.getCarrierCode().concat(segment.getNumber())));
          })
          .findFirst();
      if (optional.isPresent()) {
        target = optional.get();
      }
    } else if (adjustTypeEnum.equals(FlightAdjustTypeEnum.ADVANCE)) {
      for (FlightOfferSearch offerSearch : offerSearches) {
        String newDepartureTime = offerSearch.getItineraries()[0].getSegments()[0].getDeparture().getAt();
        if (newDepartureTime.compareTo(departureTime) < 0) {
          target = offerSearch;
          break;
        }
      }
    } else if (adjustTypeEnum.equals(FlightAdjustTypeEnum.DELAY)) {
      for (FlightOfferSearch offerSearch : offerSearches) {
        Itinerary[] itineraries1 = offerSearch.getItineraries();
        SearchSegment[] segments1 = itineraries1[itineraries1.length - 1].getSegments();
        String newArrivalTime = segments1[segments1.length - 1].getArrival().getAt();
        if (newArrivalTime.compareTo(arrivalTime) > 0) {
          target = offerSearch;
          break;
        }
      }
    }
    return flightSearchService.convertToFlightInfo(target);
  }
}