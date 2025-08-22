package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.Hotel;
import com.amadeus.resources.HotelOfferSearch;
import com.amadeus.resources.HotelOfferSearch.HotelPrice;
import com.amadeus.resources.HotelOfferSearch.QualifiedFreeText;
import com.pkfare.trip.scale.api.amadeus.hotelbycity.request.QueryHotelByCityRequest;
import com.pkfare.trip.scale.api.amadeus.hoteloffers.request.HotelOffersSearchRequest;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusHotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HotelSearchService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class HotelSearchServiceTest {

    @Mock
    private AmadeusHotelService amadeusHotelService;

    @InjectMocks
    private HotelSearchService hotelSearchService;

    private GeneratePlanParam testParam;
    private List<FlightInfo> testFlights;

    @BeforeEach
    void setUp() {
        testParam = createTestParam();
        testFlights = createTestFlights();
    }

    @Test
    void testSearchHotels_Success() {
        // Given
        Hotel[] mockHotels = createMockHotels();
        HotelOfferSearch[] mockOffers = createMockHotelOffers();
        
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenReturn(mockHotels);
        when(amadeusHotelService.searchHotelOffers(any(HotelOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证每个城市都调用了搜索
        verify(amadeusHotelService, times(testParam.getTrip_routes().size()))
            .searchHotelsByCity(any(QueryHotelByCityRequest.class));
        verify(amadeusHotelService, times(testParam.getTrip_routes().size()))
            .searchHotelOffers(any(HotelOffersSearchRequest.class));
        
        // 验证返回的酒店信息
        HotelInfo hotelInfo = result.get(0);
        assertNotNull(hotelInfo.getHotelId());
        assertNotNull(hotelInfo.getHotelName());
        assertNotNull(hotelInfo.getTotalPrice());
        assertNotNull(hotelInfo.getCheckInDate());
        assertNotNull(hotelInfo.getCheckOutDate());
    }

    @Test
    void testSearchHotels_NoHotelsFound() {
        // Given
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenReturn(new Hotel[0]);

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // 验证不会调用酒店报价搜索
        verify(amadeusHotelService, never()).searchHotelOffers(any());
    }

    @Test
    void testSearchHotels_NoOffersFound() {
        // Given
        Hotel[] mockHotels = createMockHotels();
        
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenReturn(mockHotels);
        when(amadeusHotelService.searchHotelOffers(any(HotelOffersSearchRequest.class)))
            .thenReturn(new HotelOfferSearch[0]);

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchHotels_ApiException() {
        // Given
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenThrow(new RuntimeException("API Error"));

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchHotels_CheckInOutDatesCalculation() {
        // Given
        Hotel[] mockHotels = createMockHotels();
        HotelOfferSearch[] mockOffers = createMockHotelOffers();
        
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenReturn(mockHotels);
        when(amadeusHotelService.searchHotelOffers(any(HotelOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证入住退房日期计算正确
        HotelInfo hotelInfo = result.get(0);
        assertNotNull(hotelInfo.getCheckInDate());
        assertNotNull(hotelInfo.getCheckOutDate());
        assertTrue(hotelInfo.getCheckOutDate().isAfter(hotelInfo.getCheckInDate()));
        
        // 验证住宿夜数计算正确
        assertTrue(hotelInfo.getNights() > 0);
    }

    @Test
    void testSearchHotels_PriceFiltering() {
        // Given
        Hotel[] mockHotels = createMockHotels();
        HotelOfferSearch[] mockOffers = createMockHotelOffersWithDifferentPrices();
        
        when(amadeusHotelService.searchHotelsByCity(any(QueryHotelByCityRequest.class)))
            .thenReturn(mockHotels);
        when(amadeusHotelService.searchHotelOffers(any(HotelOffersSearchRequest.class)))
            .thenReturn(mockOffers);

        // When
        List<HotelInfo> result = hotelSearchService.searchHotels(testParam, testFlights);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证选择了最便宜的酒店
        HotelInfo selectedHotel = result.get(0);
        assertEquals(new BigDecimal("100.00"), selectedHotel.getTotalPrice());
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
        param.setRoom_quantity(1);
        
        TripRouteParam route1 = new TripRouteParam();
        route1.setDestination_city("Rome");
        route1.setLocation_code("FCO");
        route1.setCountry_code("IT");
        route1.setStay_days(7);
        
        TripRouteParam route2 = new TripRouteParam();
        route2.setDestination_city("Milan");
        route2.setLocation_code("MXP");
        route2.setCountry_code("IT");
        route2.setStay_days(7);
        
        param.setTrip_routes(Arrays.asList(route1, route2));
        return param;
    }

    private List<FlightInfo> createTestFlights() {
        FlightInfo flight = new FlightInfo();
        flight.setOneWay(false);
        flight.setTotal("2000.00");
        flight.setCurrency("CNY");
        return Collections.singletonList(flight);
    }

    private Hotel[] createMockHotels() {
        Hotel hotel = mock(Hotel.class);
        when(hotel.getHotelId()).thenReturn("hotel-123");
        when(hotel.getName()).thenReturn("Test Hotel");
        return new Hotel[]{hotel};
    }

    private HotelOfferSearch[] createMockHotelOffers() {
        HotelOfferSearch offer = mock(HotelOfferSearch.class);
        
        // Mock hotel
        HotelOfferSearch.Hotel hotel = mock(HotelOfferSearch.Hotel.class);
        when(hotel.getHotelId()).thenReturn("hotel-123");
        when(hotel.getName()).thenReturn("Test Hotel");
        when(hotel.getDupeId()).thenReturn("dupe-123");
        when(hotel.getLatitude()).thenReturn(41.9028);
        when(hotel.getLongitude()).thenReturn(12.4964);
        when(offer.getHotel()).thenReturn(hotel);
        
        // Mock offers
        HotelOfferSearch.Offer hotelOffer = mock(HotelOfferSearch.Offer.class);
        when(hotelOffer.getId()).thenReturn("offer-123");
        
        // Mock price
        HotelPrice price = mock(HotelPrice.class);
        when(price.getTotal()).thenReturn("500.00");
        when(price.getCurrency()).thenReturn("CNY");
        when(hotelOffer.getPrice()).thenReturn(price);
        
        // Mock description
        QualifiedFreeText description = mock(QualifiedFreeText.class);
        when(description.getLang()).thenReturn("en");
        when(description.getText()).thenReturn("Test hotel description");
        when(hotelOffer.getDescription()).thenReturn(description);
        
        when(offer.getOffers()).thenReturn(new HotelOfferSearch.Offer[]{hotelOffer});
        
        return new HotelOfferSearch[]{offer};
    }

    private HotelOfferSearch[] createMockHotelOffersWithDifferentPrices() {
        HotelOfferSearch offer1 = mock(HotelOfferSearch.class);
        HotelOfferSearch offer2 = mock(HotelOfferSearch.class);
        
        // Mock first hotel (cheaper)
        HotelOfferSearch.Hotel hotel1 = mock(HotelOfferSearch.Hotel.class);
        when(hotel1.getHotelId()).thenReturn("hotel-1");
        when(hotel1.getName()).thenReturn("Cheap Hotel");
        when(offer1.getHotel()).thenReturn(hotel1);
        
        HotelOfferSearch.Offer hotelOffer1 = mock(HotelOfferSearch.Offer.class);
        HotelPrice price1 = mock(HotelPrice.class);
        when(price1.getTotal()).thenReturn("100.00");
        when(price1.getCurrency()).thenReturn("CNY");
        when(hotelOffer1.getPrice()).thenReturn(price1);
        when(hotelOffer1.getId()).thenReturn("offer-1");

        QualifiedFreeText description1 = mock(QualifiedFreeText.class);
        when(description1.getLang()).thenReturn("en");
        when(description1.getText()).thenReturn("Cheap hotel");
        when(hotelOffer1.getDescription()).thenReturn(description1);
        
        when(offer1.getOffers()).thenReturn(new HotelOfferSearch.Offer[]{hotelOffer1});
        
        // Mock second hotel (expensive)
        HotelOfferSearch.Hotel hotel2 = mock(HotelOfferSearch.Hotel.class);
        when(hotel2.getHotelId()).thenReturn("hotel-2");
        when(hotel2.getName()).thenReturn("Expensive Hotel");
        when(offer2.getHotel()).thenReturn(hotel2);
        
        HotelOfferSearch.Offer hotelOffer2 = mock(HotelOfferSearch.Offer.class);
        HotelPrice price2 = mock(HotelPrice.class);
        when(price2.getTotal()).thenReturn("500.00");
        when(price2.getCurrency()).thenReturn("CNY");
        when(hotelOffer2.getPrice()).thenReturn(price2);
        when(hotelOffer2.getId()).thenReturn("offer-2");

        QualifiedFreeText description2 = mock(QualifiedFreeText.class);
        when(description2.getLang()).thenReturn("en");
        when(description2.getText()).thenReturn("Expensive hotel");
        when(hotelOffer2.getDescription()).thenReturn(description2);
        
        when(offer2.getOffers()).thenReturn(new HotelOfferSearch.Offer[]{hotelOffer2});
        
        return new HotelOfferSearch[]{offer1, offer2};
    }
}
