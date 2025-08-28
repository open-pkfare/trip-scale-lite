package com.pkfare.trip.scale.plan.service.impl;

import com.pkfare.trip.scale.exception.TripPlanException;
import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;
import com.pkfare.trip.scale.plan.service.TripPlanAdjustInterface;
import com.pkfare.trip.scale.plan.service.param.AdjustPlanParam;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.response.HotelInfo;
import com.pkfare.trip.scale.plan.service.response.TripPlan;
import com.pkfare.trip.scale.service.plan.HotelSearchService;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 酒店调整服务实现类
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class HotelAdjustServiceImpl implements TripPlanAdjustInterface {
`
  @Autowired
  private HotelSearchService hotelSearchService;

  @Override
  public void adjust(GeneratePlanParam generatePlanParam, TripPlan tripPlan, AdjustPlanParam adjustPlanParam) {
    log.info("Adjusting hotel, id: {}", adjustPlanParam.getId());

    List<HotelInfo> hotels = tripPlan.getHotels();
    boolean found = false;
    for (int i = 0; i < hotels.size(); i++) {
      HotelInfo hotel = hotels.get(i);
      if (hotel.getHotelId().equals(adjustPlanParam.getId())) {
        found = true;
        // 调用搜索服务获取新酒店
        HotelInfo newHotel = hotelSearchService.searchHotels(generatePlanParam, hotel, adjustPlanParam);
        if (Objects.isNull(newHotel)) {
          throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
        }
        hotels.set(i, newHotel);
        log.info("Hotel adjusted successfully: {}", hotel.getHotelId());
        break;
      }
    }

    if (!found) {
      throw new TripPlanException(TripPlanErrorCodeEnum.NO_HOTEL_FOUND);
    }
  }
}