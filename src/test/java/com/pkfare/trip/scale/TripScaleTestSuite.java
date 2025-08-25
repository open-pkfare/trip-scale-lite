package com.pkfare.trip.scale;

import com.pkfare.trip.scale.service.plan.*;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 旅行计划系统测试套件
 * 
 * @author Trip Scale Team
 */
@Suite
@SelectClasses({
    // 服务层测试
    FlightSearchServiceTimeSlotTest.class,
    HotelSearchServiceTest.class,
    ActivitySearchServiceTest.class,
    // 外部服务测试
})
public class TripScaleTestSuite {
    // 测试套件类，用于组织和运行所有测试
}
