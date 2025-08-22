package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.FlightOfferSearch;
import com.amadeus.resources.FlightOfferSearch.AirportInfo;
import com.amadeus.resources.FlightOfferSearch.SearchSegment;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusFlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FlightSearchService时间段检查功能单元测试
 *
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FlightSearchServiceTimeSlotTest {

    @Mock
    private AmadeusFlightService amadeusFlightService;

    @InjectMocks
    private FlightSearchService flightSearchService;

    private Method isPreferredTimeSlotMethod;
    private Method parseTimeFromDateTimeMethod;

    @BeforeEach
    void setUp() throws Exception {
        // 使用反射获取私有方法进行测试
        isPreferredTimeSlotMethod = FlightSearchService.class.getDeclaredMethod(
            "isPreferredTimeSlot", FlightOfferSearch.class, boolean.class, boolean.class);
        isPreferredTimeSlotMethod.setAccessible(true);
        
        parseTimeFromDateTimeMethod = FlightSearchService.class.getDeclaredMethod(
            "parseTimeFromDateTime", String.class);
        parseTimeFromDateTimeMethod.setAccessible(true);
    }

    @Test
    void testIsPreferredTimeSlot_RoundTrip_PreferredTime() throws Exception {
        // Given - 往返航班，去程8:00，返程19:00
        FlightOfferSearch offer = createMockRoundTripOffer("2025-10-01T08:00:00", "2025-10-15T19:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, true, true);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsPreferredTimeSlot_RoundTrip_NonPreferredTime() throws Exception {
        // Given - 往返航班，去程13:00（不在6-11点），返程19:00
        FlightOfferSearch offer = createMockRoundTripOffer("2025-10-01T13:00:00", "2025-10-15T19:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, true, true);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsPreferredTimeSlot_RoundTrip_NonPreferredReturnTime() throws Exception {
        // Given - 往返航班，去程8:00，返程13:00（不在17-22点）
        FlightOfferSearch offer = createMockRoundTripOffer("2025-10-01T08:00:00", "2025-10-15T13:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, true, true);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsPreferredTimeSlot_OneWayOutbound_PreferredTime() throws Exception {
        // Given - 单程去程，8:00
        FlightOfferSearch offer = createMockOneWayOffer("2025-10-01T08:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, false, true);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsPreferredTimeSlot_OneWayOutbound_NonPreferredTime() throws Exception {
        // Given - 单程去程，13:00（不在6-11点）
        FlightOfferSearch offer = createMockOneWayOffer("2025-10-01T13:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, false, true);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsPreferredTimeSlot_OneWayInbound_PreferredTime() throws Exception {
        // Given - 单程返程，19:00
        FlightOfferSearch offer = createMockOneWayOffer("2025-10-15T19:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, false, false);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsPreferredTimeSlot_OneWayInbound_NonPreferredTime() throws Exception {
        // Given - 单程返程，8:00（不在17-22点）
        FlightOfferSearch offer = createMockOneWayOffer("2025-10-15T08:00:00");

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, false, false);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsPreferredTimeSlot_BoundaryTimes() throws Exception {
        // Given - 测试边界时间
        
        // 去程边界时间：6:00（刚好在范围内）
        FlightOfferSearch offer1 = createMockOneWayOffer("2025-10-01T06:00:00");
        boolean result1 = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer1, false, true);
        assertTrue(result1);
        
        // 去程边界时间：11:00（刚好在范围内）
        FlightOfferSearch offer2 = createMockOneWayOffer("2025-10-01T11:00:00");
        boolean result2 = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer2, false, true);
        assertTrue(result2);
        
        // 返程边界时间：17:00（刚好在范围内）
        FlightOfferSearch offer3 = createMockOneWayOffer("2025-10-15T17:00:00");
        boolean result3 = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer3, false, false);
        assertTrue(result3);
        
        // 返程边界时间：22:00（刚好在范围内）
        FlightOfferSearch offer4 = createMockOneWayOffer("2025-10-15T22:00:00");
        boolean result4 = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer4, false, false);
        assertTrue(result4);
    }

    @Test
    void testIsPreferredTimeSlot_NullOffer() throws Exception {
        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, null, false, true);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsPreferredTimeSlot_EmptyItineraries() throws Exception {
        // Given
        FlightOfferSearch offer = mock(FlightOfferSearch.class);
        when(offer.getItineraries()).thenReturn(new FlightOfferSearch.Itinerary[0]);

        // When
        boolean result = (boolean) isPreferredTimeSlotMethod.invoke(flightSearchService, offer, false, true);

        // Then
        assertFalse(result);
    }

    @Test
    void testParseTimeFromDateTime_ValidISODateTime() throws Exception {
        // Given
        String dateTimeStr = "2025-10-01T08:30:00";

        // When
        Object result = parseTimeFromDateTimeMethod.invoke(flightSearchService, dateTimeStr);

        // Then
        assertNotNull(result);
        assertEquals("08:30", result.toString());
    }

    @Test
    void testParseTimeFromDateTime_ValidISOOffsetDateTime() throws Exception {
        // Given
        String dateTimeStr = "2025-10-01T08:30:00+02:00";

        // When
        Object result = parseTimeFromDateTimeMethod.invoke(flightSearchService, dateTimeStr);

        // Then
        assertNotNull(result);
        assertEquals("08:30", result.toString());
    }

    @Test
    void testParseTimeFromDateTime_InvalidDateTime() throws Exception {
        // Given
        String dateTimeStr = "invalid-datetime";

        // When
        Object result = parseTimeFromDateTimeMethod.invoke(flightSearchService, dateTimeStr);

        // Then
        assertNull(result);
    }

    @Test
    void testParseTimeFromDateTime_NullDateTime() throws Exception {
        // When
        Object result = parseTimeFromDateTimeMethod.invoke(flightSearchService, (String) null);

        // Then
        assertNull(result);
    }

    @Test
    void testParseTimeFromDateTime_EmptyDateTime() throws Exception {
        // When
        Object result = parseTimeFromDateTimeMethod.invoke(flightSearchService, "");

        // Then
        assertNull(result);
    }

    /**
     * 创建模拟的往返航班报价
     */
    private FlightOfferSearch createMockRoundTripOffer(String outboundTime, String returnTime) {
        FlightOfferSearch offer = mock(FlightOfferSearch.class);
        
        // 创建两个行程（往返）
        FlightOfferSearch.Itinerary[] itineraries = new FlightOfferSearch.Itinerary[2];
        
        // 去程行程
        itineraries[0] = createMockItinerary(outboundTime);
        
        // 返程行程
        itineraries[1] = createMockItinerary(returnTime);
        
        when(offer.getItineraries()).thenReturn(itineraries);
        
        return offer;
    }

    /**
     * 创建模拟的单程航班报价
     */
    private FlightOfferSearch createMockOneWayOffer(String departureTime) {
        FlightOfferSearch offer = mock(FlightOfferSearch.class);
        
        // 创建一个行程（单程）
        FlightOfferSearch.Itinerary[] itineraries = new FlightOfferSearch.Itinerary[1];
        itineraries[0] = createMockItinerary(departureTime);
        
        when(offer.getItineraries()).thenReturn(itineraries);
        
        return offer;
    }

    /**
     * 创建模拟的行程
     */
    private FlightOfferSearch.Itinerary createMockItinerary(String departureTime) {
        FlightOfferSearch.Itinerary itinerary = mock(FlightOfferSearch.Itinerary.class);
        
        // 创建航段
        SearchSegment[] segments = new SearchSegment[1];
        SearchSegment segment = mock(SearchSegment.class);
        
        // 创建出发信息
        AirportInfo departure = mock(AirportInfo.class);
        when(departure.getAt()).thenReturn(departureTime);
        when(departure.getIataCode()).thenReturn("SZX");
        
        // 创建到达信息
        AirportInfo arrival = mock(AirportInfo.class);
        when(arrival.getAt()).thenReturn(departureTime); // 简化处理，实际应该是不同时间
        when(arrival.getIataCode()).thenReturn("FCO");
        
        when(segment.getDeparture()).thenReturn(departure);
        when(segment.getArrival()).thenReturn(arrival);
        when(segment.getCarrierCode()).thenReturn("CA");
        when(segment.getNumber()).thenReturn("123");
        
        segments[0] = segment;
        when(itinerary.getSegments()).thenReturn(segments);
        
        return itinerary;
    }
}
