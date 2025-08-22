package com.pkfare.trip.scale.service.plan;

import com.pkfare.trip.scale.model.dto.HotelLocationInfo;
import com.pkfare.trip.scale.model.enums.PlanStatus;
import com.pkfare.trip.scale.model.enums.TransportationType;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.*;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 计划聚合服务
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class PlanAggregationService {

  @Autowired
  private ActivitySearchService activitySearchService;

  /**
   * 聚合旅行计划
   *
   * @param param           生成计划参数
   * @param flights         航班信息列表
   * @param hotels          酒店信息列表
   * @param activities      活动信息列表
   * @param aiGeneratedPlan AI生成的计划文本
   * @return 完整的旅行计划
   */
  public TripPlan aggregateTripPlan(GeneratePlanParam param,
      List<FlightInfo> flights,
      List<HotelInfo> hotels,
      List<ActivityInfo> activities,
      TripRoutePlanResult tripRoutePlanResult) {
    log.info("Aggregating trip plan for {} routes", param.getTrip_routes().size());

    TripPlan tripPlan = new TripPlan();

    // 生成计划ID
    tripPlan.setPlanId(UUID.randomUUID().toString());

    // 设置基本信息
    tripPlan.setCurrency(param.getCurrency());
    tripPlan.setCreatedTime(LocalDateTime.now());

    // 设置航班、酒店、活动信息
    tripPlan.setFlights(flights != null ? flights : new ArrayList<>());
    tripPlan.setHotels(hotels != null ? hotels : new ArrayList<>());
    tripPlan.setActivities(activities != null ? activities : new ArrayList<>());

    // 计算总费用
    BigDecimal totalCost = calculateTotalCost(flights, hotels, activities);
    tripPlan.setTotalCost(totalCost);

    // 确定计划状态
    PlanStatus status = determinePlanStatus(totalCost, param.getBudgets(), flights, hotels);
    tripPlan.setStatus(status);

    // 设置错误信息（如果有）
    if (status != PlanStatus.SUCCESS) {
      tripPlan.setErrorMessage(getErrorMessage(status, totalCost, param.getBudgets()));
    }

    // 构建每日行程安排
    List<DailySchedule> dailySchedules = buildDailySchedules(param, hotels, activities);
    tripPlan.setDailySchedules(dailySchedules);

    // 设置AI生成的计划
    //tripPlan.setAiGeneratedPlan(aiGeneratedPlan);

    log.info("Trip plan aggregated successfully: planId={}, status={}, totalCost={}",
        tripPlan.getPlanId(), tripPlan.getStatus(), tripPlan.getTotalCost());

    return tripPlan;
  }

  /**
   * 计算总费用
   *
   * @param flights    航班信息列表
   * @param hotels     酒店信息列表
   * @param activities 活动信息列表
   * @return 总费用
   */
  private BigDecimal calculateTotalCost(List<FlightInfo> flights,
      List<HotelInfo> hotels,
      List<ActivityInfo> activities) {
    BigDecimal totalCost = BigDecimal.ZERO;

    // 计算航班费用
    if (flights != null) {
      for (FlightInfo flight : flights) {
        if (flight.getTotal() != null) {
          totalCost = PriceUtil.add(totalCost, PriceUtil.parsePrice(flight.getTotal()));
        }
      }
    }

    // 计算酒店费用
    if (hotels != null) {
      for (HotelInfo hotel : hotels) {
        if (hotel.getTotalPrice() != null) {
          totalCost = PriceUtil.add(totalCost, hotel.getTotalPrice());
        }
      }
    }

    // 计算活动费用
    if (activities != null) {
      for (ActivityInfo activity : activities) {
        if (activity.getPrice() != null) {
          totalCost = PriceUtil.add(totalCost, activity.getPrice());
        }
      }
    }

    log.info("Total cost calculated: {}", totalCost);
    return totalCost;
  }

  /**
   * 确定计划状态
   *
   * @param totalCost 总费用
   * @param budgetStr 预算字符串
   * @param flights   航班信息
   * @param hotels    酒店信息
   * @return 计划状态
   */
  private PlanStatus determinePlanStatus(BigDecimal totalCost, String budgetStr,
      List<FlightInfo> flights, List<HotelInfo> hotels) {
    // 检查是否有可用选项
    if ((flights == null || flights.isEmpty()) && (hotels == null || hotels.isEmpty())) {
      return PlanStatus.NO_AVAILABLE_OPTION;
    }

    // 检查预算
    BigDecimal budget = PriceUtil.parsePrice(budgetStr);
    if (totalCost.compareTo(budget) > 0) {
      return PlanStatus.OVER_BUDGET;
    }

    return PlanStatus.SUCCESS;
  }

  /**
   * 获取错误信息
   *
   * @param status    计划状态
   * @param totalCost 总费用
   * @param budgetStr 预算字符串
   * @return 错误信息
   */
  private String getErrorMessage(PlanStatus status, BigDecimal totalCost, String budgetStr) {
    switch (status) {
      case OVER_BUDGET:
        BigDecimal budget = PriceUtil.parsePrice(budgetStr);
        BigDecimal excess = totalCost.subtract(budget);
        return String.format("计划总费用 %s 超出预算 %s，超出金额：%s",
            PriceUtil.formatPrice(totalCost),
            PriceUtil.formatPrice(budget),
            PriceUtil.formatPrice(excess));
      case NO_AVAILABLE_OPTION:
        return "未找到可用的航班或酒店选项，请调整搜索条件或时间";
      case API_ERROR:
        return "外部服务调用失败，请稍后重试";
      case PARAM_ERROR:
        return "请求参数有误，请检查输入信息";
      default:
        return "未知错误";
    }
  }

  /**
   * 构建每日行程安排
   *
   * @param param      生成计划参数
   * @param hotels     酒店信息列表
   * @param activities 活动信息列表
   * @return 每日行程安排列表
   */
  private List<DailySchedule> buildDailySchedules(GeneratePlanParam param,
      List<HotelInfo> hotels,
      List<ActivityInfo> activities) {
    log.info("Building daily schedules for {} days", param.getTrip_days());

    List<DailySchedule> dailySchedules = new ArrayList<>();

    // 按城市分组酒店和活动
    Map<String, HotelInfo> hotelsByCity = groupHotelsByCity(hotels);
    Map<String, List<ActivityInfo>> activitiesByCity = groupActivitiesByCity(activities);

    LocalDate currentDate = DateUtil.parseDate(param.getStart_period());

    for (TripRouteParam route : param.getTrip_routes()) {
      String cityCode = route.getLocation_code();
      HotelInfo cityHotel = hotelsByCity.get(cityCode);
      List<ActivityInfo> cityActivities = activitiesByCity.getOrDefault(cityCode, new ArrayList<>());

      // 为每个停留天数创建日程安排
      for (int day = 0; day < route.getStay_days(); day++) {
        DailySchedule schedule = new DailySchedule();
        schedule.setDate(currentDate);
        schedule.setCityCode(cityCode);
        schedule.setCityName(route.getDestination_city());
        schedule.setHotel(cityHotel);

        // 分配活动到每一天
        List<ActivityInfo> dailyActivities = allocateActivitiesForDay(cityActivities, day, route.getStay_days());
        schedule.setActivities(dailyActivities);

        // 计算当日费用
        BigDecimal dailyCost = calculateDailyCost(cityHotel, dailyActivities, route.getStay_days());
        schedule.setDailyCost(dailyCost);

        // 添加交通信息（城市间移动）
        if (day == 0 && shouldAddTransportation(param, route)) {
          TransportationInfo transportation = buildTransportationInfo(param, route);
          schedule.setTransportation(transportation);
        }

        // 添加备注
        if (day == 0) {
          schedule.setNotes("到达 " + route.getDestination_city() + "，" + route.getReason_for_recommendation());
        }

        dailySchedules.add(schedule);
        currentDate = DateUtil.addDays(currentDate, 1);
      }
    }

    log.info("Built {} daily schedules", dailySchedules.size());
    return dailySchedules;
  }

  /**
   * 按城市分组酒店
   *
   * @param hotels 酒店列表
   * @return 城市代码与酒店的映射
   */
  private Map<String, HotelInfo> groupHotelsByCity(List<HotelInfo> hotels) {
    if (hotels == null) {
      return new HashMap<>();
    }

    return hotels.stream()
        .collect(Collectors.toMap(
            HotelInfo::getCityCode,
            hotel -> hotel,
            (existing, replacement) -> existing // 保留第一个
        ));
  }

  /**
   * 按城市分组活动
   *
   * @param activities 活动列表
   * @return 城市代码与活动列表的映射
   */
  private Map<String, List<ActivityInfo>> groupActivitiesByCity(List<ActivityInfo> activities) {
    if (activities == null) {
      return new HashMap<>();
    }

    return activities.stream()
        .collect(Collectors.groupingBy(ActivityInfo::getCityCode));
  }

  /**
   * 为指定天数分配活动
   *
   * @param cityActivities 城市活动列表
   * @param dayIndex       天数索引（从0开始）
   * @param totalDays      总天数
   * @return 当日活动列表
   */
  private List<ActivityInfo> allocateActivitiesForDay(List<ActivityInfo> cityActivities,
      int dayIndex, int totalDays) {
    if (cityActivities == null || cityActivities.isEmpty()) {
      return new ArrayList<>();
    }

    // 简单的活动分配策略：平均分配到每一天
    int activitiesPerDay = Math.max(1, cityActivities.size() / totalDays);
    int startIndex = dayIndex * activitiesPerDay;
    int endIndex = Math.min(startIndex + activitiesPerDay, cityActivities.size());

    if (startIndex >= cityActivities.size()) {
      return new ArrayList<>();
    }

    // 如果是最后一天，包含所有剩余活动
    if (dayIndex == totalDays - 1) {
      endIndex = cityActivities.size();
    }

    return new ArrayList<>(cityActivities.subList(startIndex, endIndex));
  }

  /**
   * 计算当日费用
   *
   * @param hotel      酒店信息
   * @param activities 活动列表
   * @param totalDays  总天数
   * @return 当日费用
   */
  private BigDecimal calculateDailyCost(HotelInfo hotel, List<ActivityInfo> activities, int totalDays) {
    BigDecimal dailyCost = BigDecimal.ZERO;

    // 酒店费用按天分摊
    if (hotel != null && hotel.getTotalPrice() != null) {
      BigDecimal dailyHotelCost = PriceUtil.divide(hotel.getTotalPrice(), new BigDecimal(totalDays));
      dailyCost = PriceUtil.add(dailyCost, dailyHotelCost);
    }

    // 活动费用
    if (activities != null) {
      for (ActivityInfo activity : activities) {
        if (activity.getPrice() != null) {
          dailyCost = PriceUtil.add(dailyCost, activity.getPrice());
        }
      }
    }

    return dailyCost;
  }

  /**
   * 判断是否应该添加交通信息
   *
   * @param param 参数
   * @param route 当前路线
   * @return 是否添加交通信息
   */
  private boolean shouldAddTransportation(GeneratePlanParam param, TripRouteParam route) {
    // 如果是第一个目的地，添加从出发地到目的地的交通信息
    return param.getTrip_routes().get(0).equals(route);
  }

  /**
   * 构建交通信息
   *
   * @param param 参数
   * @param route 路线
   * @return 交通信息
   */
  private TransportationInfo buildTransportationInfo(GeneratePlanParam param, TripRouteParam route) {
    TransportationInfo transportation = new TransportationInfo();
    transportation.setType(TransportationType.FLIGHT);
    transportation.setFrom(param.getOrigin());
    transportation.setTo(route.getDestination_city());
    transportation.setDescription("从 " + param.getOrigin() + " 飞往 " + route.getDestination_city());

    return transportation;
  }
}
