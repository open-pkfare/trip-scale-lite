package com.pkfare.trip.scale.service.external.amadeus;

import com.amadeus.resources.FlightDate;
import com.amadeus.resources.FlightOfferSearch;
import com.amadeus.resources.FlightOfferSearch.SearchPrice;
import com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;
import com.pkfare.trip.scale.exception.ExternalApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AmadeusFlightService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class AmadeusFlightServiceTest {

    @Mock
    private AmadeusFlightDatesAPI flightDatesAPI;

    @Mock
    private AmadeusFlightOffersSearchAPI flightOffersAPI;

    @InjectMocks
    private AmadeusFlightService amadeusFlightService;

    private FlightDatesRequest flightDatesRequest;
    private FlightOffersSearchRequest flightOffersRequest;

    @BeforeEach
    void setUp() {
        // 使用MockedConstruction来模拟构造函数
        try (MockedConstruction<AmadeusFlightDatesAPI> mockedFlightDatesAPI = 
             mockConstruction(AmadeusFlightDatesAPI.class, (mock, context) -> {
                 // 这里可以设置mock的行为
             });
             MockedConstruction<AmadeusFlightOffersSearchAPI> mockedFlightOffersAPI = 
             mockConstruction(AmadeusFlightOffersSearchAPI.class, (mock, context) -> {
                 // 这里可以设置mock的行为
             })) {
            
            amadeusFlightService = new AmadeusFlightService();
        }
        
        flightDatesRequest = createFlightDatesRequest();
        flightOffersRequest = createFlightOffersRequest();
    }

    @Test
    void testSearchFlightDates_Success() {
        // Given
        FlightDate[] expectedResults = createMockFlightDates();
        
        try (MockedConstruction<AmadeusFlightDatesAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightDatesAPI.class, (mock, context) -> {
                 when(mock.flightDates(any(FlightDatesRequest.class))).thenReturn(expectedResults);
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When
            FlightDate[] result = service.searchFlightDates(flightDatesRequest);
            
            // Then
            assertNotNull(result);
            assertEquals(expectedResults.length, result.length);
            assertEquals(expectedResults[0], result[0]);
        }
    }

    @Test
    void testSearchFlightDates_ApiException() {
        // Given
        try (MockedConstruction<AmadeusFlightDatesAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightDatesAPI.class, (mock, context) -> {
                 when(mock.flightDates(any(FlightDatesRequest.class)))
                     .thenThrow(new RuntimeException("API Error"));
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When & Then
            ExternalApiException exception = assertThrows(ExternalApiException.class, () -> {
                service.searchFlightDates(flightDatesRequest);
            });
            
            assertEquals("AMADEUS_FLIGHT_DATES_ERROR", exception.getErrorCode());
            assertTrue(exception.getErrorMessage().contains("Failed to search flight dates"));
        }
    }

    @Test
    void testSearchFlightDates_RetryMechanism() {
        // Given
        try (MockedConstruction<AmadeusFlightDatesAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightDatesAPI.class, (mock, context) -> {
                 when(mock.flightDates(any(FlightDatesRequest.class)))
                     .thenThrow(new RuntimeException("Temporary error"))
                     .thenThrow(new RuntimeException("Temporary error"))
                     .thenReturn(createMockFlightDates());
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When
            FlightDate[] result = service.searchFlightDates(flightDatesRequest);
            
            // Then
            assertNotNull(result);
            assertTrue(result.length > 0);
            
            // 验证重试了3次
            List<AmadeusFlightDatesAPI> constructed = mockedConstruction.constructed();
            assertEquals(1, constructed.size());
            verify(constructed.get(0), times(3)).flightDates(any(FlightDatesRequest.class));
        }
    }

    @Test
    void testSearchFlightOffers_Success() {
        // Given
        FlightOfferSearch[] expectedResults = createMockFlightOffers();
        
        try (MockedConstruction<AmadeusFlightOffersSearchAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightOffersSearchAPI.class, (mock, context) -> {
                 when(mock.flightOffersSearch(any(FlightOffersSearchRequest.class)))
                     .thenReturn(expectedResults);
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When
            FlightOfferSearch[] result = service.searchFlightOffers(flightOffersRequest);
            
            // Then
            assertNotNull(result);
            assertEquals(expectedResults.length, result.length);
            assertEquals(expectedResults[0], result[0]);
        }
    }

    @Test
    void testSearchFlightOffers_ApiException() {
        // Given
        try (MockedConstruction<AmadeusFlightOffersSearchAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightOffersSearchAPI.class, (mock, context) -> {
                 when(mock.flightOffersSearch(any(FlightOffersSearchRequest.class)))
                     .thenThrow(new RuntimeException("API Error"));
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When & Then
            ExternalApiException exception = assertThrows(ExternalApiException.class, () -> {
                service.searchFlightOffers(flightOffersRequest);
            });
            
            assertEquals("AMADEUS_FLIGHT_OFFERS_ERROR", exception.getErrorCode());
            assertTrue(exception.getErrorMessage().contains("Failed to search flight offers"));
        }
    }

    @Test
    void testSearchFlightOffers_MaxRetriesExceeded() {
        // Given
        try (MockedConstruction<AmadeusFlightOffersSearchAPI> mockedConstruction = 
             mockConstruction(AmadeusFlightOffersSearchAPI.class, (mock, context) -> {
                 when(mock.flightOffersSearch(any(FlightOffersSearchRequest.class)))
                     .thenThrow(new RuntimeException("Persistent error"));
             })) {
            
            AmadeusFlightService service = new AmadeusFlightService();
            
            // When & Then
            ExternalApiException exception = assertThrows(ExternalApiException.class, () -> {
                service.searchFlightOffers(flightOffersRequest);
            });
            
            assertEquals("AMADEUS_FLIGHT_OFFERS_ERROR", exception.getErrorCode());
            
            // 验证重试了最大次数
            List<AmadeusFlightOffersSearchAPI> constructed = mockedConstruction.constructed();
            assertEquals(1, constructed.size());
            verify(constructed.get(0), times(3)).flightOffersSearch(any(FlightOffersSearchRequest.class));
        }
    }

    private FlightDatesRequest createFlightDatesRequest() {
        FlightDatesRequest request = new FlightDatesRequest();
        request.setOrigin("SZX");
        request.setDestination("FCO");
        request.setDepartureDate("2025-10-01,2025-10-15");
        request.setDuration("14");
        request.setOneWay(false);
        return request;
    }

    private FlightOffersSearchRequest createFlightOffersRequest() {
        FlightOffersSearchRequest request = new FlightOffersSearchRequest();
        request.setOrigin("SZX");
        request.setDestination("FCO");
        request.setDepartureDate("2025-10-01");
        request.setReturnDate("2025-10-15");
        request.setAdults(1);
        request.setChildren(1);
        request.setInfants(0);
        request.setNonStop(true);
        request.setCurrency("CNY");
        request.setMaxPrice(7500);
        request.setMax(50);
        return request;
    }

    private FlightDate[] createMockFlightDates() {
        FlightDate flightDate = mock(FlightDate.class);
        
        FlightDate.Price price = mock(FlightDate.Price.class);
        when(price.getTotal()).thenReturn(Double.valueOf("2000.00"));
        when(flightDate.getPrice()).thenReturn(price);
        
        java.util.Date departureDate = new java.util.Date();
        java.util.Date returnDate = new java.util.Date(departureDate.getTime() + 14 * 24 * 60 * 60 * 1000L);
        when(flightDate.getDepartureDate()).thenReturn(departureDate);
        when(flightDate.getReturnDate()).thenReturn(returnDate);
        
        return new FlightDate[]{flightDate};
    }

    private FlightOfferSearch[] createMockFlightOffers() {
        FlightOfferSearch offer = mock(FlightOfferSearch.class);

        SearchPrice price = mock(SearchPrice.class);
        when(price.getTotal()).thenReturn("2500.00");
        when(price.getCurrency()).thenReturn("CNY");
        when(offer.getPrice()).thenReturn(price);
        
        return new FlightOfferSearch[]{offer};
    }
}
