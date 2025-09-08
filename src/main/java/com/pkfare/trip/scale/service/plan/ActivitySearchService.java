package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.Activity;
import com.pkfare.trip.scale.api.amadeus.activities.request.ActivitiesSearchRequest;
import com.pkfare.trip.scale.api.amadeus.activities.response.ActivityDto;
import com.pkfare.trip.scale.model.dto.HotelLocationInfo;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.ActivityInfo;
import com.pkfare.trip.scale.plan.service.response.CityLocationInfo;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusActivityService;
import com.pkfare.trip.scale.util.DoubleUtil;
import com.pkfare.trip.scale.util.LocationUtil;
import com.pkfare.trip.scale.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动搜索服务
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class ActivitySearchService {

  @Autowired
  private AmadeusActivityService amadeusActivityService;

  private static final int DEFAULT_RADIUS = 20;
  private static final int MAX_ACTIVITIES_PER_CITY = 10;
  private static final double ACTIVITY_SEARCH_RADIUS_KM = 100.0;

  /**
   * 搜索活动
   *
   * @param tripRoutes 旅游路线（用于获取经纬度）
   * @return 活动信息列表
   */
  public List<ActivityInfo> searchActivities(List<TripRouteParam> tripRoutes, List<CityLocationInfo> cityLocationInfos) {
    log.info("Searching activities for {} cityLocationInfos", cityLocationInfos.size());

    // 1. 获取每个酒店的经纬度信息
    Map<String, HotelLocationInfo> hotelLocationMap = buildHotelLocationMap(cityLocationInfos);

    // 2. 按城市分组搜索活动
    List<ActivityInfo> allActivities = new ArrayList<>();

    tripRoutes.parallelStream().forEach(tripRoute -> {
      String hotelKey = tripRoute.getDestination_city();
      HotelLocationInfo location = hotelLocationMap.get(hotelKey);

      if (location != null) {
        List<ActivityInfo> cityActivities = searchActivitiesForCity(tripRoute, location);
        allActivities.addAll(cityActivities);
      }
    });

    log.info("Found {} activities in total", allActivities.size());
    return allActivities;
  }

  /**
   * 构建酒店位置信息映射
   *
   * @param hotelCitys 酒店列表
   * @return 酒店位置信息映射
   */
  private Map<String, HotelLocationInfo> buildHotelLocationMap(List<CityLocationInfo> hotelCitys) {
    Map<String, HotelLocationInfo> locationMap = new HashMap<>();

    for (CityLocationInfo hotelCity : hotelCitys) {
      if (hotelCity.getLatitude() != 0.0 && hotelCity.getLongitude() != 0.0) {
        String hotelKey = hotelCity.getCityName();
        HotelLocationInfo location = new HotelLocationInfo(hotelCity.getLatitude(), hotelCity.getLongitude());
        locationMap.put(hotelKey, location);

        log.debug("hotelCity {} location: lat={}, lon={}",
            hotelCity.getCityName(), hotelCity.getLatitude(), hotelCity.getLongitude());
      } else {
        log.warn("hotelCity {} has no valid location information", hotelCity.getCityName());
      }
    }

    return locationMap;
  }

  /**
   * 构建酒店唯一标识
   *
   * @param hotel 酒店信息
   * @return 酒店唯一标识
   */
  private String buildHotelKey(HotelInfo hotel) {
    return hotel.getHotelId() + "_" + hotel.getDupeId() + "_" + hotel.getOfferId();
  }

  /**
   * 为指定城市搜索活动
   *
   * @param tripRoute 路线信息
   * @param location  酒店位置
   * @return 活动信息列表
   */
  private List<ActivityInfo> searchActivitiesForCity(TripRouteParam tripRoute, HotelLocationInfo location) {
    log.info("Searching activities for city {} ，location {}",
        tripRoute.getDestination_city(), location);

    ActivitiesSearchRequest request = new ActivitiesSearchRequest();
    request.setLatitude(location.getLatitude());
    request.setLongitude(location.getLongitude());
    request.setRadius(DEFAULT_RADIUS);

    try {
      List<ActivityDto>  activities = amadeusActivityService.searchActivities(request);

      if (activities != null && activities.size() > 0) {
        List<ActivityInfo> activityInfos = activities.stream()
            .map(activity -> convertToActivityInfo(activity, tripRoute.getDestination_city()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        log.info("total activityInfos : {}",activityInfos);

        // 筛选在合理距离内的活动
        List<ActivityInfo> filteredActivities = filterActivitiesByDistance(
            activityInfos, location, ACTIVITY_SEARCH_RADIUS_KM);

        // 筛选评分最高的活动
        List<ActivityInfo> topActivities = filterTopActivities(tripRoute, filteredActivities);

        log.info("Found {} activities for city {}, filtered to {} top activities",
            activityInfos.size(), tripRoute.getDestination_city(), topActivities.size());

        return topActivities;
      } else {
        log.warn("No activities found for city {}", tripRoute.getDestination_city());
      }
    } catch (Exception e) {
      log.error("Failed to search activities for city {}", tripRoute.getDestination_city(), e);
    }

    return new ArrayList<>();
  }

  /**
   * 根据距离筛选活动
   *
   * @param activities    活动列表
   * @param hotelLocation 酒店位置
   * @param maxDistanceKm 最大距离（公里）
   * @return 筛选后的活动列表
   */
  private List<ActivityInfo> filterActivitiesByDistance(List<ActivityInfo> activities,
      HotelLocationInfo hotelLocation,
      double maxDistanceKm) {
    return activities.stream()
        .filter(activity -> {
          if (activity.getLatitude() == 0.0 || activity.getLongitude() == 0.0) {
            return false;
          }

          boolean withinRadius = LocationUtil.isWithinRadius(
              hotelLocation.getLatitude(), hotelLocation.getLongitude(),
              activity.getLatitude(), activity.getLongitude(),
              maxDistanceKm
          );

          if (!withinRadius) {
            log.debug("Activity {} is too far from hotel (distance > {}km)",
                activity.getName(), maxDistanceKm);
          }

          return withinRadius;
        })
        .collect(Collectors.toList());
  }

  /**
   * 筛选评分最高的活动
   *
   * @param activities 活动列表
   * @return 筛选后的活动列表
   */
  private List<ActivityInfo> filterTopActivities(TripRouteParam tripRoute, List<ActivityInfo> activities) {
    if (activities == null || activities.isEmpty()) {
      return new ArrayList<>();
    }

    // 按评分倒序排序，选择评分最高的前N个
    return activities.stream()
        .sorted((a1, a2) -> Double.compare(a2.getRating(), a1.getRating()))
        .limit(MAX_ACTIVITIES_PER_CITY * tripRoute.getStay_days())
        .collect(Collectors.toList());
  }

  /**
   * 转换为ActivityInfo
   *
   * @param activity 活动对象
   * @param cityCode 城市代码
   * @return 活动信息
   */
  private ActivityInfo convertToActivityInfo(ActivityDto activity, String cityCode) {
    if (activity == null) {
      return null;
    }
    if(CollectionUtils.isEmpty(activity.getPictures())){
      return null;
    }

    ActivityInfo activityInfo = new ActivityInfo();

    // 基本信息
    activityInfo.setActivityId(activity.getId());
    activityInfo.setName(activity.getName());
    activityInfo.setDescription(activity.getShortDescription());
    activityInfo.setCityCode(cityCode);

    // 评分信息
    activityInfo.setRating(DoubleUtil.strToDouble(activity.getRating()));

    // 价格信息
    if (activity.getPrice() != null) {
      activityInfo.setPrice(PriceUtil.parsePrice(activity.getPrice().getAmount()));
      activityInfo.setCurrency(activity.getPrice().getCurrencyCode());
    } else {
      activityInfo.setPrice(PriceUtil.parsePrice("0"));
      activityInfo.setCurrency("EUR"); // 默认货币
    }

    // 位置信息
    if (activity.getGeoCode() != null) {
      activityInfo.setLatitude(activity.getGeoCode().getLatitude());
      activityInfo.setLongitude(activity.getGeoCode().getLongitude());
    }

    // 分类信息
    if (StringUtils.isNotBlank(activity.getType())) {
      activityInfo.setType(activityInfo.getType());
    } else {
      activityInfo.setType("General");
    }

    activityInfo.setPictures(activity.getPictures().subList(0,1));

    return activityInfo;
  }

  /**
   * 按距离对活动进行排序
   *
   * @param activities    活动列表
   * @param hotelLocation 酒店位置
   * @return 按距离排序的活动列表
   */
  public List<ActivityInfo> sortActivitiesByDistance(List<ActivityInfo> activities,
      HotelLocationInfo hotelLocation) {
    if (activities == null || activities.isEmpty() || hotelLocation == null) {
      return activities;
    }

    return activities.stream()
        .sorted((a1, a2) -> {
          double distance1 = LocationUtil.calculateDistance(
              hotelLocation.getLatitude(), hotelLocation.getLongitude(),
              a1.getLatitude(), a1.getLongitude()
          );
          double distance2 = LocationUtil.calculateDistance(
              hotelLocation.getLatitude(), hotelLocation.getLongitude(),
              a2.getLatitude(), a2.getLongitude()
          );
          return Double.compare(distance1, distance2);
        })
        .collect(Collectors.toList());
  }

  public List<ActivityInfo> searchActivitiesNearby(HotelInfo hotel, String activityType, String currency) {
    ActivitiesSearchRequest request = new ActivitiesSearchRequest();
    request.setLatitude(hotel.getLatitude());
    request.setLongitude(hotel.getLongitude());
    // todo 需要调整radius加重试
    request.setRadius(DEFAULT_RADIUS);
    if (StringUtils.isNotBlank(activityType)) {
      request.setCategoryGroup(activityType);
    }
    if (StringUtils.isNotBlank(currency)) {
      request.setCurrency(currency);
    }

    try {
      List<ActivityDto> activities = amadeusActivityService.searchActivities(request);
      if (activities == null || activities.isEmpty()) {
        return Collections.emptyList();
      }

      List<ActivityInfo> activityInfos = activities.stream()
          .map(activity -> convertToActivityInfo(activity, hotel.getCityCode()))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      // 筛选在合理距离内的活动
      List<ActivityInfo> activityInfoList = filterActivitiesByDistance(activityInfos,
          // todo  因为是mock数据，将距离限制放宽
          new HotelLocationInfo(hotel.getLatitude(), hotel.getLongitude()), 2000);

      // 筛选评分最高的活动
      List<ActivityInfo> topActivities = activityInfoList.stream()
          .sorted((a1, a2) -> Double.compare(a2.getRating(), a1.getRating()))
          .collect(Collectors.toList());

      log.info("Found {} activities for city {}, filtered to {} top activities",
          activityInfos.size(), hotel.getCityCode(), topActivities.size());

      return topActivities;
    } catch (Exception e) {
      log.error("Failed to search activities for city {}", hotel.getCityCode(), e);
      return Collections.emptyList();
    }
  }
}
