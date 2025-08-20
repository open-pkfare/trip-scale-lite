# 1.背景（Background）

一款智能旅行助手应用允许用户输入偏好与需求，或通过与 AI 助手对话来确认旅行目的地。系统需要基于收集的信息为用户生成完整的旅行计划（Trip Plan）。


# 2.需求范围（Scope）

目前已完成用户偏好和需求的信息收集，需要根据收集的信息生成旅行计划。

目标：根据用户请求参数，自动完成机票 + 酒店搜索，生成一份可行的旅行方案，按照业务流程和提供的接口，完成backend内的接口和逻辑开发。

接口入口为：com.pkfare.trip.scale.plan.service.GeneratePlanService#generatePlan。

接口方法入参为：com.pkfare.trip.scale.plan.service.param.GeneratePlanParam。

接口方法响应结果为：com.pkfare.trip.scale.plan.service.response.TripPlan。


# 3.请求参数（Request Payload）

```plain
{
    "origin":"Shenzhen",
    "location_code":"CN",
    "start_period":"2025-10-01",
    "end_period":"2025-10-14",
    "trip_days":14,
    "adult_number":1,
    "child_number":1,
    "budgets": "15000",   
    "currency":"CNY",
    "room_quantity"，2,
    "trip_routes":[
    {
        "stay_days":4,
        "destination_city":"Rome",
        "country_code":"IT",
        "location_code":"FCO",
        "reason_for_recommendation":"As the heart of the Roman Empire and home to the Vatican, Rome is an unmissable destination for anyone who loves ancient buildings, history, and religious stories."
    }, {
        "stay_days":5,
        "destination_city":"Ostia",
        "country_code":"IT",
        "location_code":"OST",
        "reason_for_recommendation":"Known for Ostia Antica’s well-preserved Roman ruins, ancient streets, and theaters, Ostia offers a fascinating glimpse of everyday life in the Roman Empire."
    }, {
        "stay_days":5,
        "destination_city":"Anzio",
        "country_code":"IT",
        "location_code":"ANZ",
        "reason_for_recommendation":"A seaside town famous for beaches and World War II history, Anzio blends sun, history, and Italian coastal culture within an hour’s trip from Rome."
    }]
}
```
项目中com.pkfare.trip.scale.plan.service.param.GeneratePlanParam类和com.pkfare.trip.scale.plan.service.param.TripRouteParam与上述参数对应，该类在项目中已生成。

请求参数解释：

用户计划在2025-10-01出发，游玩14天，返程日期是2025-10-14，出发地origin是中国深圳，旅行游客为1个成人和1个儿童，旅行预算金额为15000，币种为CNY。

行程计划如下，有三段行程：

* 第一站是坐飞机到Rome城市，在Rome这个城市玩4天，reason_for_recommendation为游玩推荐语

* 在Ostia城市玩5天，

* 在Anzio城市玩5天，然后做飞机回深圳。

现在需要根据行程计划调用接口生成整个游玩计划。

## 字段说明

### com.pkfare.trip.scale.plan.service.param.GeneratePlanParam

|字段|说明|
|:----|:----|
|origin|出发城市 IATA 码或城市名（例：Shenzhen）|
|location_code|出发国家/地区码（例：CN）|
|start_period / end_period|可接受的出行时间范围|
|trip_days|行程天数（含出发与回程）|
|adult_number / child_number|成人与儿童人数|
|budgets / currency|旅行预算及币种|
|room_quantity|酒店房间数量|
|trip_routes|行程拆分（多目的地）|

### com.pkfare.trip.scale.plan.service.param.TripRouteParam

|字段|说明|
|:----|:----|
|stay_days|出发城市 IATA 码或城市名（例：Shenzhen）在当前城市游玩天数|
|destination_city|城市名称|
|country_code|国家/地区码（例：CN、FR）|
|location_code|城市 IATA 码|
|reason_for_recommendation|推荐理由|

# 4.响应结果（Interface Response result）

项目中com.pkfare.trip.scale.plan.service.response.TripPlan类和xxxxx类与上述参数对应，该类在项目中已生成。

## 字段说明

### com.pkfare.trip.scale.plan.service.response.TripPlan

```plain
@Data
public class TripPlan {
    private String planId;                    // 计划ID，UUID生成
    private BigDecimal totalCost;             // 总费用
    private String currency;                  // 币种
    private PlanStatus status;                // 计划状态：SUCCESS/OVER_BUDGET/NO_AVAILABLE_OPTION
    private List<FlightInfo> flights;         // 航班信息列表
    private List<HotelInfo> hotels;           // 酒店信息列表
    private List<ActivityInfo> activities;    // 活动信息列表
    private List<DailySchedule> dailySchedules; // 每日行程安排
    private String aiGeneratedPlan;           // AI生成的计划文本
    private LocalDateTime createdTime;        // 创建时间
    private String errorMessage;              // 错误信息（状态非SUCCESS时）
}

```
### com.pkfare.trip.scale.plan.service.response.DailySchedule

```plain
@Data
public class DailySchedule {
    private LocalDate date;                   // 日期
    private String cityCode;                  // 城市代码
    private String cityName;                  // 城市名称
    private HotelInfo hotel;                  // 当日酒店
    private List<ActivityInfo> activities;    // 当日活动列表
    private TransportationInfo transportation; // 交通信息（城市间移动）
    private String notes;                     // 备注信息
    private BigDecimal dailyCost;             // 当日费用
}
```
### com.pkfare.trip.scale.plan.service.response

```plain
@Data
public class TransportationInfo {
    private TransportationType type;          // 交通类型：FLIGHT/TRAIN/BUS/CAR
    private String from;                      // 出发地
    private String to;                        // 目的地
    private String duration;                  // 行程时间
    private BigDecimal cost;                  // 交通费用
    private String description;               // 描述信息
    private LocalDateTime departureTime;      // 出发时间
    private LocalDateTime arrivalTime;        // 到达时间
}
```
### 其他枚举

```plain
public enum PlanStatus {
    SUCCESS("generated successfully"),
    OVER_BUDGET("over budget"),
    NO_AVAILABLE_OPTION("no available options"),
    API_ERROR("API call failed"),
    PARAM_ERROR("parameter error");
    
    private final String description;
}

public enum TransportationType {
    FLIGHT("flight"),
    TRAIN("train"),
    BUS("bus"),
    CAR("car"),
    WALK("walk");
    
    private final String description;
}

```


# 5.关键类与方法（Key Classes / Methods）

|组件|说明|
|:----|:----|
|GeneratePlanService#generatePlan|入口服务，串联整体流程；返回最终 TripPlan结果|
|AmadeusFlightDatesAPI|查询最便宜航班的往返日期价格区间。|
|AmadeusFlightOffersSearchAPI|查询具体航班报价。|
|AmadeusSearchHotelsByCityAPI|根据城市查酒店基本信息，主要使用hotelID。|
|AmadeusHotelOffersSearchAPI|查询酒店报价。|
|AmadeusActivitiesSearchApi|查询景点活动|
|GeneratePlanParam / TripRouteParam|请求数据载体。|
|TripPlan|代码生成的旅行计划数据载体|



# 6.业务流程（Business Flow）

整体流程分为 **航班查询** 、 **酒店查询** 、**查询城市景点活动** 、**提交AI大模型生成机票+酒店+旅游景点活动的旅行计划**、 **聚合返回结果** 四大阶段：

## 6.1 航班查询（Round-Trip Flights）

有两种场景：

1. **第一段行程和最后一段行程的城市一致，为往返航班。**

2. **第一段行程和最后一段行程的城市不一致，两个单程，两段行程都确定有机场。**


**整体流程为：**

1.判断GeneratePlanParam中start_period和end_period的时间间隔范围和游玩天数trip_days是否一致，将结果赋值给preciseTravel字段，boolean类型。preciseTravel = (end_period - start_period) == trip_days，若完全匹配则为 true，否则 false。


2.判断第一段行程的城市和最后一段行程的城市是否一致，将结果赋值给roundTrip，boolean类型。


3.如果preciseTravel为false，需要先查询最便宜航班日期。调用com.pkfare.trip.scale.api.amadeus.flightdates.AmadeusFlightDatesAPI#flightDates接口，如果roundTrip为true，查询往返航班，只查询一次接口，如果roundTrip为false，查询两次单程航班，需要查询接口两次。

**roundTrip为true时，参数FlightDatesRequest赋值逻辑如下**，

|字段|值|
|:----|:----|
|origin|GeneratePlanParam.origin|
|destination|取GeneratePlanParam第一段TripRoute的location_code。|
|departureDate|取GeneratePlanParam.start_period和GeneratePlanParam.end_period-trip_days，这两个日期拼接成字符串，中间用英文逗号隔开。|
|duration|取GeneratePlanParam.trip_days|
|oneWay|roundTrip为true时，取false，否则取false|

航班结果筛选：

   * 去程和返程的航安时间间隔天数等于GeneratePlanParam中的trip_days天数。

   * 往返行程最便宜的航班，价格使用FlightDate.price.total字段。

返回结果：最便宜往返航班的出发日期departureDate和返程日期returnDate。

**roundTrip为false时，参数FlightDatesRequest赋值逻辑如下，**

|字段|值|
|:----|:----|
|origin|去程：GeneratePlanParam.origin返程：GeneratePlanParam最后一段TripRoute的location_code。|
|destination|去程：GeneratePlanParam最后一段TripRoute的location_code。返程：GeneratePlanParam.origin|
|departureDate|去程：GeneratePlanParam.start_period和GeneratePlanParam.end_period-trip_days，这两个日期拼接成字符串，中间用英文逗号隔开。返程：GeneratePlanParam.start_period+trip_days和GeneratePlanParam.end_period，这两个日期拼接成字符串，中间用英文逗号隔开|
|duration|oneWay为false时取GeneratePlanParam.trip_days，否则为空|
|oneWay|roundTrip为true时，取false，否则取true|

航班结果筛选：

   * 满足去程单程航班和返程单程航安的时间间隔天数等于GeneratePlanParam中的trip_days天数

   * 取两个行程航班总价最便宜的两个航班。

返回结果：两个航班的出发日期departureDate和返程日期returnDate。


4.如果preciseTravel为true并且roundTrip为true，直接调用com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI#flightOffersSearch接口查询往返航班。

FlightOffersSearchRequest赋值如下：

|字段|值|
|:----|:----|
|origin|GeneratePlanParam.origin|
|destination|取GeneratePlanParam第一段TripRoute的location_code。|
|departureDate|departureDate取GeneratePlanParam.start_period|
|returnDate|returnDate取GeneratePlanParam.end_period|
|adults|取GeneratePlanParam.adult_number。|
|children|取GeneratePlanParam.child_number。|
|infants|0|
|nonStop|true|
|currency|取GeneratePlanParam.currency。|
|maxPrice|取GeneratePlanParam.budgets除以2。|
|max|50。|

航班结果筛选。

* 首选往返航班总价最低。

* 去程选择早上 06:00–11:00 的航班，返程选择 17:00–22:00 的航班。


5.如果preciseTravel为true并且roundTrip为false，需要调用2词com.pkfare.trip.scale.api.amadeus.flightoffers.AmadeusFlightOffersSearchAPI#flightOffersSearch接口查询两个单程航班。

FlightOffersSearchRequest赋值如下：

|origin|去程：GeneratePlanParam.origin返程：GeneratePlanParam最后一段TripRoute的location_code。|
|:----|:----|
|destination|去程：取GeneratePlanParam第一段TripRoute的location_code。返程：GeneratePlanParam.origin|
|departureDate|去程：取GeneratePlanParam.start_period返程：取GeneratePlanParam.end_period|
|returnDate|空|
|adults|取GeneratePlanParam.adult_number。|
|children|取GeneratePlanParam.child_number。|
|infants|0|
|nonStop|true|
|currency|取GeneratePlanParam.currency。|
|maxPrice|取GeneratePlanParam.budgets除以2。|
|max|50。|

航班结果筛选。

* 两个行程航班单价最低。

* 去程选择早上 06:00–11:00 的航班，返程选择 17:00–22:00 的航班。


航班信息定义：

```plain
public class FlightInfo {
  private Boolean oneWay;            //是否单程
  private String total;              //航班价格
  private private String currency;   // 价格币种 
  private List<ItineraryInfo> itineraries;  // 行程集合

}
public class ItineraryInfo {
  private List<SegmentInfo> segments; //航段
}

public class SegmentInfo {
  private String departure;        //出发地址
  priavte String departureTime;    // 出发时间
  private String arrival;          // 到达地址
  private String arrivalTime;      // 到达时间
  private String carrierCode;      // 航司
  priavte String number;           // 航班号
}
```


## 6.2 酒店查询（Hotels）

1.根据目的地城市拉取酒店列表。

遍历GeneratePlanParam的TripRoute集合获取location_code组成locationCodeList集合，调用com.pkfare.trip.scale.api.amadeus.hotelbycity.AmadeusSearchHotelsByCityAPI#queryHotelByCity接口，根据location_code对返回Hotel的hotelId进行分组组装localHotelIdMap，获得每个locationCode对应的hotelIdList集合。请求参数赋值如下：

|字段|值|
|:----|:----|
|cityCode|取对应TripRoute.location_code|
|radius|20|
|radiusUnit|KM|

返回值：hotelId 列表，将其按 location_code 聚合为 localHotelIdMap<location_code, List<hotelId>>。


2.逐段查询最优报价

* 遍历每个 GeneratePlanParam.TripRoute 调用 AmadeusHotelOffersSearchAPI#hotelOffersSearch，筛选出每个TripRoute对应的最便宜酒店，组装成酒店集合。价格最便宜筛选使用接口返回实体类中HotelOfferSearch.offers.price.total字段。

* 接口请求参数赋值如下：

酒店查询 依赖 航班查询返回的日期来确定查询酒店需要的checkInDate和checkOutDate时间。

|字段|值|
|:----|:----|
|hotelIds|localHotelIdMap中对应location_code的hotelIdList。localHotelIdMap.get(location_code)|
|checkInDate|第一个TripRoute对应checkInDate取机票往返航班的去程到达时间，后续每个TripRoute的checkInDate为上一个TripRoute的checkOutDate。|
|checkOutDate|每一个TripRoute的checkOutDate取它的checkInDate加上当前TripRoute.stay_days。|
|adults|GeneratePlanParam.adult_number+GeneratePlanParam.child_number。|
|countryOfResidence|每段TripRoute.country_code。|
|roomQuantity|取GeneratePlanParam.room_quantity。|
|priceRange|固定为"10,5000"|
|currency|取GeneratePlanParam.currency。|

酒店信息定义：

```plain
public class HotelInfo {
    private String hotelId;                   // 酒店ID
    private String dupeId;                    // 重复ID
    private String offerId;                   // 报价ID
    private String hotelName;                 // 酒店名称
    private String cityCode;                  // 城市代码
    private LocalDate checkInDate;            // 入住日期
    private LocalDate checkOutDate;           // 退房日期
    private int nights;                       // 住宿夜数
    private BigDecimal totalPrice;            // 总价格
    private String currency;                  // 币种
    private double latitude;                  // 纬度
    private double longitude;                 // 经度
    private String address;                   // 地址
    private String descriptionLang;           // 描述语言
    private String descriptionText;           // 描述文案
}
```
## **6.3 城市景点活动查询**

1.获取每段TripRoute对应酒店的经纬度，组装成map，key为AmadeusHotelOffersSearchAPI#hotelOffersSearch接口返回的hotelId+dupeId+offerId，value为酒店经纬度组成的实体，该实体两个字段，latitude和longitude，double类型。

2.遍历TripRoute集合分别调用

com.pkfare.trip.scale.api.amadeus.activities.AmadeusActivitiesSearchApi#searchActivities接口查询景点活动。

接口请求参数赋值如下：

城市景点活动查询 依赖 酒店查询返回酒店经纬度信息。

|字段|值|
|:----|:----|
|latitude|每个TripRoute对应酒店的latitude|
|longitude|每个TripRoute对应酒店的longitude|
|radius|20|

景点结果筛选：

   * 选择评级最高的5个景点活动，使用Activity.rating字段，倒序。


景点活动信息定义：

```plain
public class ActivityInfo {
    private String activityId;                // 活动ID
    private String name;                      // 活动名称
    private String description;               // 活动描述
    private String cityCode;                  // 城市代码
    private double rating;                    // 评分
    private BigDecimal price;                 // 价格
    private String currency;                  // 币种
    private double latitude;                  // 纬度
    private double longitude;                 // 经度
    private String category;                  // 活动类别
}
```
## **6.4 将机票、酒店、景点活动数据提交AI大模型生成机票+酒店+旅游景点活动的旅行计划**

逻辑和流程：

*  构建AI提示词，输出模版到com.pkfare.trip.scale.agent.inspiration.PlanningPrompt类，提示词需要结合输入的SubmitAiPlanInfo信息，包括GeneratePlanParam、航班、酒店、景点活动信息。

* 调用Gemini生成计划，结合com.pkfare.trip.scale.agent.planning.PlanningAgent。

* 解析AI响应并构建TripPlan。


提示词关键点：

* 保证机票、酒店、活动景点日期和时间连续 且 城市顺序正确。

* 各城市之间输出交通工具和行程时间。

* 活动景点选择需结合活动与酒店经纬度，筛选 100km 内适中距离，活动景点的行程按从酒店由近及远安排。


大模型：gemini-2.5-pro


输入参数：

```plain
public class SubmitAiPlanInfo {
    private GeneratePlanParam generatePlanParam;
    private List<FlightInfo> flightInfos;     // 航班信息
    private List<HotelInfo> hotelInfos;       // 酒店信息列表
    private List<ActivityInfo> activityInfos; // 活动信息列表
}
```


## **6.5 聚合返回结果**

结合com.pkfare.trip.scale.plan.service.response.TripPlan封装返回结果。


# 7.数据流程图（Sequence Diagram）

待cursor生成。



# 8.失败与异常处理（Error Handling）

待cursor生成。



# 9.测试策略（Testing）

## 单元测试（Unit Tests）

* 单元测试框架：JUnit5 + Mockito。

* 每个Service类都需要完整的单元测试。

* 完整业务流程的单元测试。

* Mock Amadeus API结果。




 









