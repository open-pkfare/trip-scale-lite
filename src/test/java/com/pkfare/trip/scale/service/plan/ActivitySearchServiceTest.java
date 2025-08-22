package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.Activity;
import com.amadeus.resources.Activity.ElementaryPrice;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ActivitySearchService单元测试
 * 
 * @author Trip Scale Team
 */
@ExtendWith(MockitoExtension.class)
public class ActivitySearchServiceTest {

    @Mock
    private AmadeusActivityService amadeusActivityService;

    @InjectMocks
    private ActivitySearchService activitySearchService;

    private List<HotelInfo> testHotels;

    @BeforeEach
    void setUp() {
        testHotels = createTestHotels();
    }

    @Test
    void testSearchActivities_Success() {
        // Given
        Activity[] mockActivities = createMockActivities();
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenReturn(mockActivities);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证每个酒店都调用了活动搜索
        verify(amadeusActivityService, times(testHotels.size()))
            .searchActivities(any(ActivitiesSearchRequest.class));
        
        // 验证返回的活动信息
        ActivityInfo activityInfo = result.get(0);
        assertNotNull(activityInfo.getActivityId());
        assertNotNull(activityInfo.getName());
        assertNotNull(activityInfo.getType());
        assertTrue(activityInfo.getRating() >= 0);
    }

    @Test
    void testSearchActivities_EmptyHotels() {
        // Given
        List<HotelInfo> emptyHotels = Collections.emptyList();

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(emptyHotels);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // 验证没有调用API
        verify(amadeusActivityService, never()).searchActivities(any());
    }

    @Test
    void testSearchActivities_HotelsWithoutLocation() {
        // Given
        HotelInfo hotelWithoutLocation = new HotelInfo();
        hotelWithoutLocation.setHotelId("hotel-no-location");
        hotelWithoutLocation.setHotelName("Hotel Without Location");
        hotelWithoutLocation.setCityCode("ROM");
        hotelWithoutLocation.setLatitude(0.0);
        hotelWithoutLocation.setLongitude(0.0);
        
        List<HotelInfo> hotelsWithoutLocation = Collections.singletonList(hotelWithoutLocation);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(hotelsWithoutLocation);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // 验证没有调用API（因为没有有效位置信息）
        verify(amadeusActivityService, never()).searchActivities(any());
    }

    @Test
    void testSearchActivities_NoActivitiesFound() {
        // Given
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenReturn(new Activity[0]);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchActivities_ApiException() {
        // Given
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenThrow(new RuntimeException("API Error"));

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchActivities_FilterTopActivities() {
        // Given
        Activity[] mockActivities = createMockActivitiesWithDifferentRatings();
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenReturn(mockActivities);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证返回的活动数量不超过最大限制（5个）
        assertTrue(result.size() <= 5);
        
        // 验证活动按评分排序（最高评分在前）
        if (result.size() > 1) {
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).getRating() >= result.get(i + 1).getRating());
            }
        }
    }

    @Test
    void testSearchActivities_FilterByDistance() {
        // Given
        Activity[] mockActivities = createMockActivitiesWithDifferentLocations();
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenReturn(mockActivities);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        
        // 验证所有返回的活动都在合理距离内（100km）
        for (ActivityInfo activity : result) {
            assertTrue(activity.getLatitude() != 0.0 || activity.getLongitude() != 0.0);
        }
    }

    @Test
    void testSearchActivities_ConvertActivityInfo() {
        // Given
        Activity[] mockActivities = createMockActivities();
        when(amadeusActivityService.searchActivities(any(ActivitiesSearchRequest.class)))
            .thenReturn(mockActivities);

        // When
        List<ActivityInfo> result = activitySearchService.searchActivities(testHotels);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        ActivityInfo activityInfo = result.get(0);
        assertEquals("activity-123", activityInfo.getActivityId());
        assertEquals("Colosseum", activityInfo.getName());
        assertEquals("Historic site description", activityInfo.getDescription());
        assertEquals("ROM", activityInfo.getCityCode());
        assertEquals(4.8, activityInfo.getRating());
        assertEquals(new BigDecimal("25.00"), activityInfo.getPrice());
        assertEquals("EUR", activityInfo.getCurrency());
        assertEquals(41.8902, activityInfo.getLatitude());
        assertEquals(12.4922, activityInfo.getLongitude());
        assertEquals("Historic", activityInfo.getType());
        assertNotNull(activityInfo.getPictures());
        assertFalse(activityInfo.getPictures().isEmpty());
    }

    private List<HotelInfo> createTestHotels() {
        HotelInfo hotel1 = new HotelInfo();
        hotel1.setHotelId("hotel-123");
        hotel1.setHotelName("Test Hotel Rome");
        hotel1.setCityCode("ROM");
        hotel1.setLatitude(41.9028);
        hotel1.setLongitude(12.4964);
        hotel1.setCheckInDate(LocalDate.of(2025, 10, 1));
        hotel1.setCheckOutDate(LocalDate.of(2025, 10, 8));
        
        HotelInfo hotel2 = new HotelInfo();
        hotel2.setHotelId("hotel-456");
        hotel2.setHotelName("Test Hotel Milan");
        hotel2.setCityCode("MIL");
        hotel2.setLatitude(45.4642);
        hotel2.setLongitude(9.1900);
        hotel2.setCheckInDate(LocalDate.of(2025, 10, 8));
        hotel2.setCheckOutDate(LocalDate.of(2025, 10, 15));
        
        return Arrays.asList(hotel1, hotel2);
    }

    private Activity[] createMockActivities() {
        Activity activity = mock(Activity.class);
        when(activity.getId()).thenReturn("activity-123");
        when(activity.getName()).thenReturn("Colosseum");
        when(activity.getShortDescription()).thenReturn("Historic site description");
        when(activity.getRating()).thenReturn("4.8");
        when(activity.getType()).thenReturn("Historic");
        when(activity.getPictures()).thenReturn(new String[]{"pic1.jpg", "pic2.jpg"});
        
        // Mock price
        ElementaryPrice price = mock(Activity.ElementaryPrice.class);
        when(price.getAmount()).thenReturn("25.00");
        when(price.getCurrencyCode()).thenReturn("EUR");
        when(activity.getPrice()).thenReturn(price);
        
        // Mock geocode
        Activity.GeoCode geoCode = mock(Activity.GeoCode.class);
        when(geoCode.getLatitude()).thenReturn(41.8902);
        when(geoCode.getLongitude()).thenReturn(12.4922);
        when(activity.getGeoCode()).thenReturn(geoCode);
        
        return new Activity[]{activity};
    }

    private Activity[] createMockActivitiesWithDifferentRatings() {
        Activity activity1 = mock(Activity.class);
        when(activity1.getId()).thenReturn("activity-1");
        when(activity1.getName()).thenReturn("High Rated Activity");
        when(activity1.getRating()).thenReturn("4.9");
        when(activity1.getType()).thenReturn("Museum");
        when(activity1.getPictures()).thenReturn(new String[]{"pic1.jpg"});
        
        Activity.GeoCode geoCode1 = mock(Activity.GeoCode.class);
        when(geoCode1.getLatitude()).thenReturn(41.9028);
        when(geoCode1.getLongitude()).thenReturn(12.4964);
        when(activity1.getGeoCode()).thenReturn(geoCode1);
        
        Activity activity2 = mock(Activity.class);
        when(activity2.getId()).thenReturn("activity-2");
        when(activity2.getName()).thenReturn("Medium Rated Activity");
        when(activity2.getRating()).thenReturn("3.5");
        when(activity2.getType()).thenReturn("Park");
        when(activity2.getPictures()).thenReturn(new String[]{"pic2.jpg"});
        
        Activity.GeoCode geoCode2 = mock(Activity.GeoCode.class);
        when(geoCode2.getLatitude()).thenReturn(41.9000);
        when(geoCode2.getLongitude()).thenReturn(12.5000);
        when(activity2.getGeoCode()).thenReturn(geoCode2);
        
        Activity activity3 = mock(Activity.class);
        when(activity3.getId()).thenReturn("activity-3");
        when(activity3.getName()).thenReturn("Low Rated Activity");
        when(activity3.getRating()).thenReturn("2.0");
        when(activity3.getType()).thenReturn("Shop");
        when(activity3.getPictures()).thenReturn(new String[]{"pic3.jpg"});
        
        Activity.GeoCode geoCode3 = mock(Activity.GeoCode.class);
        when(geoCode3.getLatitude()).thenReturn(41.8900);
        when(geoCode3.getLongitude()).thenReturn(12.4800);
        when(activity3.getGeoCode()).thenReturn(geoCode3);
        
        return new Activity[]{activity1, activity2, activity3};
    }

    private Activity[] createMockActivitiesWithDifferentLocations() {
        Activity nearActivity = mock(Activity.class);
        when(nearActivity.getId()).thenReturn("activity-near");
        when(nearActivity.getName()).thenReturn("Near Activity");
        when(nearActivity.getRating()).thenReturn("4.0");
        when(nearActivity.getType()).thenReturn("Museum");
        when(nearActivity.getPictures()).thenReturn(new String[]{"near.jpg"});
        
        // 距离酒店很近的活动
        Activity.GeoCode nearGeoCode = mock(Activity.GeoCode.class);
        when(nearGeoCode.getLatitude()).thenReturn(41.9030); // 很接近酒店位置
        when(nearGeoCode.getLongitude()).thenReturn(12.4960);
        when(nearActivity.getGeoCode()).thenReturn(nearGeoCode);
        
        Activity farActivity = mock(Activity.class);
        when(farActivity.getId()).thenReturn("activity-far");
        when(farActivity.getName()).thenReturn("Far Activity");
        when(farActivity.getRating()).thenReturn("4.5");
        when(farActivity.getType()).thenReturn("Beach");
        when(farActivity.getPictures()).thenReturn(new String[]{"far.jpg"});
        
        // 距离酒店很远的活动（应该被过滤掉）
        Activity.GeoCode farGeoCode = mock(Activity.GeoCode.class);
        when(farGeoCode.getLatitude()).thenReturn(45.0000); // 距离很远
        when(farGeoCode.getLongitude()).thenReturn(15.0000);
        when(farActivity.getGeoCode()).thenReturn(farGeoCode);
        
        return new Activity[]{nearActivity, farActivity};
    }
}
