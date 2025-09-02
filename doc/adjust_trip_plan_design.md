你是一个经验丰富、逻辑严谨的资深Java开发工程师。请为我实现这样的功能，且尽量不改动已有类：

1. 调整已生成好的旅行计划；这份旅行计划中有航班、酒店、每日活动，对应com.pkfare.trip.scale.plan.service.response.TripPlan类
2. 用户可能要求调整某个航班的出发日期，或更换价格更低的航班；航班对应com.pkfare.trip.scale.plan.service.response.FlightInfo类
3. 用户可能要求调整某天的酒店星级，或更换符合偏好的酒店；酒店对应com.pkfare.trip.scale.plan.service.response.HotelInfo类
4. 用户可能要求增加或取消某天内的某项活动；活动对应com.pkfare.trip.scale.plan.service.response.ActivityInfo类

首先，在src/main/java/com/pkfare/trip/scale/controller包下，参照TripPlanController.java，生成一个名为TripPlanAdjustController，其中引用com.pkfare.trip.scale.plan.service.TripPlanAdjustService。

TripPlanAdjustController中提供一个名为adjustPlan的接口，，参数列表有

1. com.pkfare.trip.scale.plan.service.param.GeneratePlanParam
2. com.pkfare.trip.scale.plan.service.response.TripPlan
3. com.pkfare.trip.scale.plan.service.param.AdjustPlanParam的集合

AdjustPlanParam类中有属性对应下面的json

```json
{
      "item": "flight",
      "id": "FL-20231001-001",
      "adjustType": "replace/advance/delay/cheaper/changeDepartureAirport",
      "noStop": "Air China",
      "newDepartureAirport": "HKG",
      "timeChange": "1",
      "maxPrice": 450.00,
	  "preference":""
}
```

在TripPlanAdjustService中，分别实现调整航班、酒店、活动的方法，最终响应调整后的TripPlan。

1. 获取新航班，可以调用com.pkfare.trip.scale.service.plan.FlightSearchService#searchFlightOffers方法；
2. 获取新酒店，可以调用com.pkfare.trip.scale.service.plan.HotelSearchService#searchHotels
3. 获取新活动，可以调用com.pkfare.trip.scale.service.plan.ActivitySearchService#searchActivities

