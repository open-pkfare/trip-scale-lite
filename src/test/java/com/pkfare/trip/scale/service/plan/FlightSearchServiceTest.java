package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.FlightDate;
import com.amadeus.resources.FlightOfferSearch;
import com.amadeus.resources.FlightOfferSearch.AirportInfo;
import com.amadeus.resources.FlightOfferSearch.SearchPrice;
import com.amadeus.resources.FlightOfferSearch.SearchSegment;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusFlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FlightSearchService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class FlightSearchServiceTest {

    @Mock
    private AmadeusFlightService amadeusFlightService;

    @InjectMocks
    private FlightSearchService flightSearchService;

    private GeneratePlanParam testParam;

    @BeforeEach
    void setUp() {
        testParam = createTestParam();
    }

    @Test
    void testSearchFlights_PreciseTravel_RoundTrip() {
        // Given
        boolean preciseTravel = true;
        boolean roundTrip = true;
        
        FlightOfferSearch[] mockOffers = createMockFlightOffers();
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<FlightInfo> result = flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证不调用FlightDates API（因为是精确时间）
        verify(amadeusFlightService, never()).searchFlightDates(any());
        
        // 验证调用FlightOffers API
        verify(amadeusFlightService).searchFlightOffers(any(FlightOffersSearchRequest.class));
    }

    @Test
    void testSearchFlights_NonPreciseTravel_RoundTrip() {
        // Given
        boolean preciseTravel = false;
        boolean roundTrip = true;
        
        FlightDate[] mockDates = createMockFlightDates();
        FlightOfferSearch[] mockOffers = createMockFlightOffers();
        
        when(amadeusFlightService.searchFlightDates(any(FlightDatesRequest.class)))
            .thenReturn(mockDates);
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<FlightInfo> result = flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);

        // Then
        assertNotNull(result);
        
        // 验证先调用FlightDates API
        verify(amadeusFlightService).searchFlightDates(any(FlightDatesRequest.class));
        
        // 然后调用FlightOffers API
        verify(amadeusFlightService).searchFlightOffers(any(FlightOffersSearchRequest.class));
    }

    @Test
    void testSearchFlights_NonPreciseTravel_OneWay() {
        // Given
        boolean preciseTravel = false;
        boolean roundTrip = false;
        
        FlightDate[] mockDates = createMockFlightDates();
        FlightOfferSearch[] mockOffers = createMockFlightOffers();
        
        when(amadeusFlightService.searchFlightDates(any(FlightDatesRequest.class)))
            .thenReturn(mockDates);
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<FlightInfo> result = flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);

        // Then
        assertNotNull(result);
        
        // 验证调用FlightDates API两次（去程和返程）
        verify(amadeusFlightService, times(2)).searchFlightDates(any(FlightDatesRequest.class));
        
        // 验证调用FlightOffers API两次（去程和返程）
        verify(amadeusFlightService, times(2)).searchFlightOffers(any(FlightOffersSearchRequest.class));
    }

    @Test
    void testSearchFlights_EmptyResults() {
        // Given
        boolean preciseTravel = true;
        boolean roundTrip = true;
        
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenReturn(new FlightOfferSearch[0]);

        // When
        List<FlightInfo> result = flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchFlights_NullResults() {
        // Given
        boolean preciseTravel = true;
        boolean roundTrip = true;
        
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenReturn(null);

        // When
        List<FlightInfo> result = flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchFlights_ApiException() {
        // Given
        boolean preciseTravel = true;
        boolean roundTrip = true;
        
        when(amadeusFlightService.searchFlightOffers(any(FlightOffersSearchRequest.class)))
            .thenThrow(new RuntimeException("API Error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            flightSearchService.searchFlights(testParam, preciseTravel, roundTrip);
        });
    }

    private GeneratePlanParam createTestParam() {
        GeneratePlanParam param = new GeneratePlanParam();
        param.setOrigin("Shenzhen");
        param.setStart_period("2025-10-01");
        param.setEnd_period("2025-10-14");
        param.setTrip_days(14);
        param.setAdult_number(1);
        param.setChild_number(1);
        param.setBudgets("15000");
        param.setCurrency("CNY");
        
        TripRouteParam route1 = new TripRouteParam();
        route1.setDestination_city("Rome");
        route1.setLocation_code("FCO");
        route1.setStay_days(7);
        
        TripRouteParam route2 = new TripRouteParam();
        route2.setDestination_city("Milan");
        route2.setLocation_code("MXP");
        route2.setStay_days(7);
        
        param.setTrip_routes(Arrays.asList(route1, route2));
        return param;
    }

    private FlightDate[] createMockFlightDates() {
        FlightDate flightDate = mock(FlightDate.class);
        
        // Mock price
        FlightDate.Price price = mock(FlightDate.Price.class);
        when(price.getTotal()).thenReturn(Double.valueOf("1000.00"));
        when(flightDate.getPrice()).thenReturn(price);
        
        // Mock dates
        java.util.Date departureDate = new java.util.Date();
        java.util.Date returnDate = new java.util.Date(departureDate.getTime() + 14 * 24 * 60 * 60 * 1000L);
        when(flightDate.getDepartureDate()).thenReturn(departureDate);
        when(flightDate.getReturnDate()).thenReturn(returnDate);
        
        return new FlightDate[]{flightDate};
    }

    private FlightOfferSearch[] createMockFlightOffers() {
        FlightOfferSearch offer = mock(FlightOfferSearch.class);
        
        // Mock price
        SearchPrice price = mock(SearchPrice.class);
        when(price.getTotal()).thenReturn("2000.00");
        when(price.getCurrency()).thenReturn("CNY");
        when(offer.getPrice()).thenReturn(price);
        
        // Mock itineraries
        FlightOfferSearch.Itinerary[] itineraries = new FlightOfferSearch.Itinerary[1];
        FlightOfferSearch.Itinerary itinerary = mock(FlightOfferSearch.Itinerary.class);
        
        // Mock segments
        SearchSegment[] segments = new SearchSegment[1];
        SearchSegment segment = mock(SearchSegment.class);
        
        // Mock departure and arrival
        AirportInfo departure = mock(AirportInfo.class);
        AirportInfo arrival = mock(AirportInfo.class);
        
        when(departure.getIataCode()).thenReturn("SZX");
        when(departure.getAt()).thenReturn("2025-10-01T08:00:00");
        when(arrival.getIataCode()).thenReturn("FCO");
        when(arrival.getAt()).thenReturn("2025-10-01T14:00:00");
        
        when(segment.getDeparture()).thenReturn(departure);
        when(segment.getArrival()).thenReturn(arrival);
        when(segment.getCarrierCode()).thenReturn("CA");
        when(segment.getNumber()).thenReturn("123");
        
        segments[0] = segment;
        when(itinerary.getSegments()).thenReturn(segments);
        
        itineraries[0] = itinerary;
        when(offer.getItineraries()).thenReturn(itineraries);
        
        return new FlightOfferSearch[]{offer};
    }
}
