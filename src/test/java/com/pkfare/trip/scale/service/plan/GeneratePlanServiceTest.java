package com.pkfare.trip.scale.service.plan;


import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.Lists;
import com.pkfare.trip.scale.plan.service.GeneratePlanService;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.TripRoutePlanResult;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
public class GeneratePlanServiceTest {

  @Resource
  private GeneratePlanService generatePlanService;


  /**
   * 精确行程日期 && 往返航班
   */
  @Test
  void testPlan_preciseTravel_roundTrip() {
    GeneratePlanParam param = buildPreciseTravelRoundTripParam();
    TripRoutePlanResult result = generatePlanService.generatePlan(param);
    assertNotNull(result);
  }

  private GeneratePlanParam buildPreciseTravelRoundTripParam() {
    GeneratePlanParam param = new GeneratePlanParam();
    param.setOrigin("Shenzhen");
    param.setLocation_code("CN");
    param.setStart_period("2025-10-01");
    param.setEnd_period("2025-10-07");
    param.setTrip_days(7);
    param.setAdult_number(1);
    param.setChild_number(1);
    param.setRoom_quantity(1);
    param.setBudgets("10000");
    param.setCurrency("CNY");
    param.setTrip_routes(buildRoundTripRoutes());
    return param;
  }

  private List<TripRouteParam> buildRoundTripRoutes() {
    List<TripRouteParam> tripRouteParams = Lists.newArrayList();
    tripRouteParams.add(buildRouteTrip(2, "Rome", "IT", "FCO"));
    tripRouteParams.add(buildRouteTrip(2, "Ostia", "IT", "OST"));
    tripRouteParams.add(buildRouteTrip(3, "Anzio", "IT", "ANZ"));
    return tripRouteParams;
  }

  private TripRouteParam buildRouteTrip(int days, String destinationCity, String countryCode, String locationCode) {
    TripRouteParam param = new TripRouteParam();
    param.setStay_days(days);
    param.setDestination_city(destinationCity);
    param.setLocation_code(locationCode);
    param.setCountry_code(countryCode);
    param.setReason_for_recommendation("recommendation");
    return param;
  }

}
