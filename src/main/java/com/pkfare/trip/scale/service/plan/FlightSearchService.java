package com.pkfare.trip.scale.service.plan;

import com.amadeus.resources.FlightDate;
import com.amadeus.resources.FlightOfferSearch;
import com.amadeus.resources.FlightOfferSearch.Itinerary;
import com.amadeus.resources.FlightOfferSearch.SearchSegment;
import com.amadeus.resources.Location;
import com.pkfare.trip.scale.api.amadeus.airportlocations.AmadeusFlightAirportLocationSearchAPI;
import com.pkfare.trip.scale.api.amadeus.airportlocations.request.FlightAirportLocationSearchRequest;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightoffers.request.FlightOffersSearchRequest;


import com.pkfare.trip.scale.model.dto.FlightSearchResult;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import com.pkfare.trip.scale.plan.service.response.FlightInfo;
import com.pkfare.trip.scale.plan.service.response.FlightLocationInfo;
import com.pkfare.trip.scale.plan.service.response.GeoInfo;
import com.pkfare.trip.scale.plan.service.response.ItineraryInfo;
import com.pkfare.trip.scale.plan.service.response.SegmentInfo;
import com.pkfare.trip.scale.service.external.amadeus.AmadeusFlightService;
import com.pkfare.trip.scale.util.DateUtil;
import com.pkfare.trip.scale.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 航班搜索服务
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class FlightSearchService {
    
    @Autowired
    private AmadeusFlightService amadeusFlightService;
    
    private static final LocalTime MORNING_START = LocalTime.of(6, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime EVENING_START = LocalTime.of(17, 0);
    private static final LocalTime EVENING_END = LocalTime.of(22, 0);
    
    /**
     * 搜索航班
     * 
     * @param param 搜索参数
     * @param preciseTravel 是否精确时间
     * @param roundTrip 是否往返
     * @return 航班信息列表
     */
    public List<FlightInfo> searchFlights(GeneratePlanParam param, boolean preciseTravel, boolean roundTrip) {
        log.info("Searching flights: preciseTravel={}, roundTrip={}", preciseTravel, roundTrip);
        
        FlightSearchResult dateResult = null;
        
        // 如果不是精确时间，先搜索最便宜的日期
        if (!preciseTravel) {
            dateResult = searchFlightDates(param, roundTrip);
        }
        
        // 搜索具体航班报价
        List<FlightInfo> flightInfoList = searchFlightOffers(param, dateResult, preciseTravel, roundTrip);
        return flightInfoList;
    }

    private List<FlightLocationInfo> queryFlightLocations(List<FlightInfo> flightInfoList) {
        
        if (flightInfoList == null || flightInfoList.isEmpty()) {
            log.info("Flight info list is empty, returning empty location list");
            return new ArrayList<>();
        }
        
        // 1. 从航班信息中提取所有机场代码
        Set<String> airportCodes = extractAirportCodes(flightInfoList);
        if (airportCodes.isEmpty()) {
            log.warn("No airport codes found in flight info list");
            return new ArrayList<>();
        }
        
        log.info("Extracted airport codes: {}", airportCodes);
        
        // 2. 查询机场位置信息
        List<FlightLocationInfo> locationInfoList = new ArrayList<>();
        AmadeusFlightAirportLocationSearchAPI locationSearchAPI = new AmadeusFlightAirportLocationSearchAPI();
        
        for (String airportCode : airportCodes) {
            try {
                FlightAirportLocationSearchRequest request = new FlightAirportLocationSearchRequest();
                request.setKeyword(airportCode);
                
                Location[] locations = locationSearchAPI.queryFlightLocation(request);
                
                if (locations != null && locations.length > 0) {
                    // 3. 转换为FlightLocationInfo
                    List<FlightLocationInfo> convertedLocations = convertToFlightLocationInfo(locations, airportCode);
                    locationInfoList.addAll(convertedLocations);
                }
            } catch (Exception e) {
                log.error("Failed to query location for airport code: {}", airportCode, e);
                // 继续处理其他机场代码，不因单个失败而中断整个流程
            }
        }
        
        log.info("Successfully queried {} flight locations", locationInfoList.size());
        return locationInfoList;
    }
    
    /**
     * 从航班信息列表中提取所有机场代码
     * 
     * @param flightInfoList 航班信息列表
     * @return 机场代码集合
     */
    private Set<String> extractAirportCodes(List<FlightInfo> flightInfoList) {
        Set<String> airportCodes = new HashSet<>();
        
        for (FlightInfo flightInfo : flightInfoList) {
            if (flightInfo.getItineraries() != null) {
                for (ItineraryInfo itinerary : flightInfo.getItineraries()) {
                    if (itinerary.getSegments() != null) {
                        for (SegmentInfo segment : itinerary.getSegments()) {
                            // 添加出发机场代码
                            if (segment.getDeparture() != null && !segment.getDeparture().trim().isEmpty()) {
                                airportCodes.add(segment.getDeparture().trim());
                            }
                            // 添加到达机场代码
                            if (segment.getArrival() != null && !segment.getArrival().trim().isEmpty()) {
                                airportCodes.add(segment.getArrival().trim());
                            }
                        }
                    }
                }
            }
        }
        
        return airportCodes;
    }
    
    /**
     * 将Amadeus Location数组转换为FlightLocationInfo列表
     * 
     * @param locations Amadeus Location数组
     * @param airportCode 查询的机场代码
     * @return FlightLocationInfo列表
     */
    private List<FlightLocationInfo> convertToFlightLocationInfo(Location[] locations, String airportCode) {
        List<FlightLocationInfo> locationInfoList = new ArrayList<>();
        
        for (Location location : locations) {
            try {
                FlightLocationInfo locationInfo = new FlightLocationInfo();
                
                // 设置机场代码
                locationInfo.setAirport(airportCode);
                
                // 设置经纬度信息
                GeoInfo geoInfo = new GeoInfo();
                locationInfo.setGeoInfo(geoInfo);
                if (location.getGeoCode() != null) {
                    geoInfo.setLatitude(location.getGeoCode().getLatitude());
                    geoInfo.setLongitude(location.getGeoCode().getLongitude());
                }
                
                locationInfoList.add(locationInfo);
                
                log.debug("Converted location for airport {}: lat={}, lng={}", 
                    airportCode, locationInfo.getGeoInfo().getLatitude(), locationInfo.getGeoInfo().getLongitude());
                
            } catch (Exception e) {
                log.error("Failed to convert location for airport code: {}", airportCode, e);
                // 继续处理其他位置信息
            }
        }
        
        return locationInfoList;
    }

    /**
     * 将位置信息补充到航班信息中
     * 
     * @param flightInfoList 航班信息列表
     * @param locations 位置信息列表
     * @return 补充了位置信息的航班信息列表
     */
    private List<FlightInfo> supplementFlightLocations(List<FlightInfo> flightInfoList, List<FlightLocationInfo> locations) {
        if (flightInfoList == null || flightInfoList.isEmpty()) {
            log.info("Flight info list is empty, no location supplementation needed");
            return flightInfoList;
        }
        
        if (locations == null || locations.isEmpty()) {
            log.warn("Location info list is empty, cannot supplement flight locations");
            return flightInfoList;
        }
        
        // 1. 创建机场代码到位置信息的映射
        Map<String, GeoInfo> airportLocationMap = createAirportLocationMap(locations);
        
        log.info("Created airport location map with {} entries", airportLocationMap.size());
        
        // 2. 遍历航班信息，补充位置信息
        for (FlightInfo flightInfo : flightInfoList) {
            if (flightInfo.getItineraries() != null) {
                for (ItineraryInfo itinerary : flightInfo.getItineraries()) {
                    if (itinerary.getSegments() != null) {
                        for (SegmentInfo segment : itinerary.getSegments()) {
                            // 补充出发地位置信息
                            if (segment.getDeparture() != null) {
                                GeoInfo departureGeo = airportLocationMap.get(segment.getDeparture().trim());
                                if (departureGeo != null) {
                                    segment.setDepartureGeo(copyGeoInfo(departureGeo));
                                    log.debug("Supplemented departure geo for airport: {}", segment.getDeparture());
                                }
                            }
                            
                            // 补充到达地位置信息
                            if (segment.getArrival() != null) {
                                GeoInfo arrivalGeo = airportLocationMap.get(segment.getArrival().trim());
                                if (arrivalGeo != null) {
                                    segment.setArrivalGeo(copyGeoInfo(arrivalGeo));
                                    log.debug("Supplemented arrival geo for airport: {}", segment.getArrival());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        log.info("Successfully supplemented location information for {} flights", flightInfoList.size());
        return flightInfoList;
    }
    
    /**
     * 创建机场代码到位置信息的映射
     * 
     * @param locations 位置信息列表
     * @return 机场代码到GeoInfo的映射
     */
    private Map<String, GeoInfo> createAirportLocationMap(List<FlightLocationInfo> locations) {
        Map<String, GeoInfo> airportLocationMap = new HashMap<>();
        
        for (FlightLocationInfo locationInfo : locations) {
            if (locationInfo.getAirport() != null && locationInfo.getGeoInfo() != null) {
                String airportCode = locationInfo.getAirport().trim();
                airportLocationMap.put(airportCode, locationInfo.getGeoInfo());
                
                log.debug("Added airport {} to location map: lat={}, lng={}", 
                    airportCode, 
                    locationInfo.getGeoInfo().getLatitude(), 
                    locationInfo.getGeoInfo().getLongitude());
            }
        }
        
        return airportLocationMap;
    }
    
    /**
     * 复制GeoInfo对象，避免引用共享
     * 
     * @param original 原始GeoInfo对象
     * @return 复制的GeoInfo对象
     */
    private GeoInfo copyGeoInfo(GeoInfo original) {
        if (original == null) {
            return null;
        }
        
        GeoInfo copy = new GeoInfo();
        copy.setLatitude(original.getLatitude());
        copy.setLongitude(original.getLongitude());
        return copy;
    }

    /**
     * 搜索最便宜的航班日期
     * 
     * @param param 搜索参数
     * @param roundTrip 是否往返
     * @return 航班日期搜索结果
     */
    public FlightSearchResult searchFlightDates(GeneratePlanParam param, boolean roundTrip) {
        log.info("Searching flight dates for roundTrip: {}", roundTrip);
        
        FlightSearchResult result = new FlightSearchResult();
        
        if (roundTrip) {
            // 往返航班搜索
            FlightDatesRequest request = buildFlightDatesRequest(param, true);
            FlightDate[] flightDates = amadeusFlightService.searchFlightDates(request);
            
            if (flightDates != null && flightDates.length > 0) {
                // 筛选出去程和返程间隔等于trip_days且价格最低的航班
                FlightDate bestFlight = findBestRoundTripFlightDate(flightDates, param.getTrip_days());
                if (bestFlight != null) {
                    result.setDepartureDate(convertToLocalDate(bestFlight.getDepartureDate()));
                    result.setReturnDate(convertToLocalDate(bestFlight.getReturnDate()));
                }
            }
        } else {
            // 两个单程航班搜索
            searchOneWayFlightDates(param, result);
        }
        
        return result;
    }
    
    /**
     * 搜索单程航班日期
     * 
     * @param param 搜索参数
     * @param result 结果对象
     */
    private void searchOneWayFlightDates(GeneratePlanParam param, FlightSearchResult result) {
        List<TripRouteParam> routes = param.getTrip_routes();
        String firstDestination = routes.get(0).getLocation_code();
        String lastDestination = routes.get(routes.size() - 1).getLocation_code();
        
        // 搜索去程
        FlightDatesRequest outboundRequest = buildOneWayFlightDatesRequest(
            param.getOrigin(), firstDestination, param.getStart_period(), param.getEnd_period(), param.getTrip_days(), true,param.getBudgets());
        FlightDate[] outboundDates = amadeusFlightService.searchFlightDates(outboundRequest);
        
        // 搜索返程
        FlightDatesRequest returnRequest = buildOneWayFlightDatesRequest(
            lastDestination, param.getOrigin(), param.getStart_period(), param.getEnd_period(), param.getTrip_days(), false,param.getBudgets());
        FlightDate[] returnDates = amadeusFlightService.searchFlightDates(returnRequest);
        
        // 找到最佳组合
        findBestOneWayFlightDates(outboundDates, returnDates, param.getTrip_days(), result);
    }
    
    /**
     * 搜索具体航班报价
     * 
     * @param param 搜索参数
     * @param dateResult 日期搜索结果
     * @param preciseTravel 是否精确时间
     * @param roundTrip 是否往返
     * @return 航班信息列表
     */
    public List<FlightInfo> searchFlightOffers(GeneratePlanParam param, FlightSearchResult dateResult,
                                               boolean preciseTravel, boolean roundTrip) {
        log.info("Searching flight offers");
        
        List<FlightInfo> flights = new ArrayList<>();
        
        if (roundTrip) {
            // 往返航班搜索
            FlightOffersSearchRequest request = buildFlightOffersRequest(param, dateResult, preciseTravel, true);
            FlightOfferSearch[] offers = amadeusFlightService.searchFlightOffers(request);
            
            if (offers != null && offers.length > 0) {
                List<FlightInfo> bestFlights = filterBestFlights(Arrays.asList(offers), true, true);
                flights.addAll(bestFlights);
            }
        } else {
            // 两个单程航班搜索
            flights.addAll(searchOneWayFlightOffers(param, dateResult, preciseTravel));
        }
        List<FlightLocationInfo> locations = queryFlightLocations(flights);
        return supplementFlightLocations(flights,locations);
    }
    
    /**
     * 搜索单程航班报价
     * 
     * @param param 搜索参数
     * @param dateResult 日期搜索结果
     * @param preciseTravel 是否精确时间
     * @return 航班信息列表
     */
    private List<FlightInfo> searchOneWayFlightOffers(GeneratePlanParam param, FlightSearchResult dateResult, boolean preciseTravel) {
        List<FlightInfo> flights = new ArrayList<>();
        List<TripRouteParam> routes = param.getTrip_routes();
        
        // 去程航班
        FlightOffersSearchRequest outboundRequest = buildOneWayFlightOffersRequest(
            param, dateResult, preciseTravel, true);
        FlightOfferSearch[] outboundOffers = amadeusFlightService.searchFlightOffers(outboundRequest);
        
        if (outboundOffers != null && outboundOffers.length > 0) {
            List<FlightInfo> outboundFlights = filterBestFlights(Arrays.asList(outboundOffers), false, true);
            flights.addAll(outboundFlights);
        }
        
        // 返程航班
        FlightOffersSearchRequest returnRequest = buildOneWayFlightOffersRequest(
            param, dateResult, preciseTravel, false);
        FlightOfferSearch[] returnOffers = amadeusFlightService.searchFlightOffers(returnRequest);
        
        if (returnOffers != null && returnOffers.length > 0) {
            List<FlightInfo> returnFlights = filterBestFlights(Arrays.asList(returnOffers), false, false);
            flights.addAll(returnFlights);
        }
        
        return flights;
    }
    
    /**
     * 筛选最佳航班
     * 
     * @param offers 航班报价列表
     * @param isRoundTrip 是否往返
     * @return 筛选后的航班信息列表
     */
    private List<FlightInfo> filterBestFlights(List<FlightOfferSearch> offers, boolean isRoundTrip, boolean isOutward) {
        if (offers == null || offers.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 按价格排序
        offers.sort((o1, o2) -> {
            BigDecimal price1 = PriceUtil.parsePrice(o1.getPrice().getTotal());
            BigDecimal price2 = PriceUtil.parsePrice(o2.getPrice().getTotal());
            return price1.compareTo(price2);
        });
        
        // 在价格最低的几个选项中，选择时间最合适的
        List<FlightOfferSearch> topOffers = offers.stream()
            .limit(Math.min(10, offers.size()))
            .collect(Collectors.toList());
        
        FlightOfferSearch bestOffer = findBestTimeSlotFlight(topOffers,isRoundTrip,isOutward);
        
        if (bestOffer != null) {
            FlightInfo flightInfo = convertToFlightInfo(bestOffer);
            return Collections.singletonList(flightInfo);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 构建航班日期搜索请求
     * 
     * @param param 参数
     * @param isRoundTrip 是否往返
     * @return 请求对象
     */
    private FlightDatesRequest buildFlightDatesRequest(GeneratePlanParam param, boolean isRoundTrip) {
        FlightDatesRequest request = new FlightDatesRequest();
        request.setOrigin(param.getOrigin());
        request.setDestination(param.getTrip_routes().get(0).getLocation_code());
        
        LocalDate startDate = DateUtil.parseDate(param.getStart_period());
        LocalDate endDate = DateUtil.parseDate(param.getEnd_period());
        LocalDate adjustedEndDate = DateUtil.minusDays(endDate, param.getTrip_days());
        
        request.setDepartureDate(DateUtil.buildDateRange(startDate, adjustedEndDate));
        request.setDuration(String.valueOf(param.getTrip_days()));
        request.setOneWay(!isRoundTrip);
        request.setMaxPrice(PriceUtil.formatPrice(param.getBudgets()));
        
        return request;
    }
    
    /**
     * 构建单程航班日期搜索请求
     */
    private FlightDatesRequest buildOneWayFlightDatesRequest(String origin, String destination, 
                                                           String startPeriod, String endPeriod, 
                                                           int tripDays, boolean isOutbound, String budgets) {
        FlightDatesRequest request = new FlightDatesRequest();
        request.setOrigin(origin);
        request.setDestination(destination);
        request.setOneWay(true);
        
        LocalDate startDate = DateUtil.parseDate(startPeriod);
        LocalDate endDate = DateUtil.parseDate(endPeriod);
        
        if (isOutbound) {
            LocalDate adjustedEndDate = DateUtil.minusDays(endDate, tripDays);
            request.setDepartureDate(DateUtil.buildDateRange(startDate, adjustedEndDate));
        } else {
            LocalDate adjustedStartDate = DateUtil.addDays(startDate, tripDays);
            request.setDepartureDate(DateUtil.buildDateRange(adjustedStartDate, endDate));
        }

        request.setMaxPrice(PriceUtil.formatPrice(budgets));
        return request;
    }
    
    /**
     * 构建航班报价搜索请求
     */
    private FlightOffersSearchRequest buildFlightOffersRequest(GeneratePlanParam param, 
                                                              FlightSearchResult dateResult, 
                                                              boolean preciseTravel, 
                                                              boolean isRoundTrip) {
        FlightOffersSearchRequest request = new FlightOffersSearchRequest();
        request.setOrigin(param.getOrigin());
        request.setDestination(param.getTrip_routes().get(0).getLocation_code());
        request.setAdults(param.getAdult_number());
        request.setChildren(param.getChild_number());
        request.setInfants(0);
        request.setNonStop(true);
        request.setCurrency(param.getCurrency());
        request.setMaxPrice(PriceUtil.divide(PriceUtil.parsePrice(param.getBudgets()), new BigDecimal("2")).intValue());
        request.setMax(50);
        
        if (preciseTravel) {
            request.setDepartureDate(param.getStart_period());
            if (isRoundTrip) {
                request.setReturnDate(param.getEnd_period());
            }
        } else if (dateResult != null) {
            if (dateResult.getDepartureDate() != null) {
                request.setDepartureDate(DateUtil.formatDate(dateResult.getDepartureDate()));
            }
            if (dateResult.getReturnDate() != null && isRoundTrip) {
                request.setReturnDate(DateUtil.formatDate(dateResult.getReturnDate()));
            }
        }
        
        return request;
    }
    
    /**
     * 构建单程航班报价搜索请求
     */
    private FlightOffersSearchRequest buildOneWayFlightOffersRequest(GeneratePlanParam param, 
                                                                    FlightSearchResult dateResult, 
                                                                    boolean preciseTravel, 
                                                                    boolean isOutbound) {
        FlightOffersSearchRequest request = new FlightOffersSearchRequest();
        request.setAdults(param.getAdult_number());
        request.setChildren(param.getChild_number());
        request.setInfants(0);
        request.setNonStop(true);
        request.setCurrency(param.getCurrency());
        request.setMaxPrice(PriceUtil.divide(PriceUtil.parsePrice(param.getBudgets()), new BigDecimal("2")).intValue());
        request.setMax(50);
        
        List<TripRouteParam> routes = param.getTrip_routes();
        
        if (isOutbound) {
            request.setOrigin(param.getOrigin());
            request.setDestination(routes.get(0).getLocation_code());
            
            if (preciseTravel) {
                request.setDepartureDate(param.getStart_period());
            } else if (dateResult != null && dateResult.getDepartureDate() != null) {
                request.setDepartureDate(DateUtil.formatDate(dateResult.getDepartureDate()));
            }
        } else {
            request.setOrigin(routes.get(routes.size() - 1).getLocation_code());
            request.setDestination(param.getOrigin());
            
            if (preciseTravel) {
                request.setDepartureDate(param.getEnd_period());
            } else if (dateResult != null && dateResult.getReturnDate() != null) {
                request.setDepartureDate(DateUtil.formatDate(dateResult.getReturnDate()));
            }
        }
        
        return request;
    }
    
    /**
     * 找到最佳往返航班日期
     */
    private FlightDate findBestRoundTripFlightDate(FlightDate[] flightDates, int tripDays) {
        return Arrays.stream(flightDates)
            .filter(fd -> {
                if (fd.getDepartureDate() == null || fd.getReturnDate() == null) {
                    return false;
                }
                LocalDate depDate = convertToLocalDate(fd.getDepartureDate());
                LocalDate retDate = convertToLocalDate(fd.getReturnDate());
                return DateUtil.daysBetween(depDate, retDate) == tripDays;
            })
            .min(Comparator.comparing(fd -> PriceUtil.parsePrice(fd.getPrice().getTotal())))
            .orElse(null);
    }
    
    /**
     * 找到最佳单程航班日期组合
     */
    private void findBestOneWayFlightDates(FlightDate[] outboundDates, FlightDate[] returnDates, 
                                         int tripDays, FlightSearchResult result) {
        if (outboundDates == null || returnDates == null || 
            outboundDates.length == 0 || returnDates.length == 0) {
            return;
        }
        
        BigDecimal bestTotalPrice = null;
        FlightDate bestOutbound = null;
        FlightDate bestReturn = null;
        
        for (FlightDate outbound : outboundDates) {
            for (FlightDate returnFlight : returnDates) {
                LocalDate outDate = convertToLocalDate(outbound.getDepartureDate());
                LocalDate retDate = convertToLocalDate(returnFlight.getDepartureDate());
                
                if (DateUtil.daysBetween(outDate, retDate) == tripDays) {
                    BigDecimal totalPrice = PriceUtil.add(
                        PriceUtil.parsePrice(outbound.getPrice().getTotal()),
                        PriceUtil.parsePrice(returnFlight.getPrice().getTotal())
                    );
                    
                    if (bestTotalPrice == null || totalPrice.compareTo(bestTotalPrice) < 0) {
                        bestTotalPrice = totalPrice;
                        bestOutbound = outbound;
                        bestReturn = returnFlight;
                    }
                }
            }
        }
        
        if (bestOutbound != null && bestReturn != null) {
            result.setDepartureDate(convertToLocalDate(bestOutbound.getDepartureDate()));
            result.setReturnDate(convertToLocalDate(bestReturn.getDepartureDate()));
        }
    }
    
    /**
     * 找到最佳时间段的航班
     */
    private FlightOfferSearch findBestTimeSlotFlight(List<FlightOfferSearch> offers, boolean isRoundTrip,boolean isOutward) {
        // 优先选择去程早上6-11点，返程17-22点的航班
        for (FlightOfferSearch offer : offers) {
            if (isPreferredTimeSlot(offer,isRoundTrip,isOutward)) {
                return offer;
            }
        }
        
        // 如果没有理想时间段的航班，返回价格最低的
        return offers.get(0);
    }
    
    /**
     * 检查是否为首选时间段
     * 
     * @param offer 航班报价
     * @param isOutward 是否为去程（仅在单程时使用）
     * @return 是否为首选时间段
     */
    private boolean isPreferredTimeSlot(FlightOfferSearch offer, boolean isRoundTrip, boolean isOutward) {
        if (offer == null || offer.getItineraries() == null || offer.getItineraries().length == 0) {
            return false;
        }

        
        if (isRoundTrip) {
            // 往返航班：检查去程早上6-11点，返程17-22点
            return checkOutboundTimeSlot(offer.getItineraries()[0]) && 
                   checkInboundTimeSlot(offer.getItineraries()[1]);
        } else {
            // 单程航班
            if (isOutward) {
                // 去程：检查早上6-11点
                return checkOutboundTimeSlot(offer.getItineraries()[0]);
            } else {
                // 返程：检查17-22点
                return checkInboundTimeSlot(offer.getItineraries()[0]);
            }
        }
    }
    
    /**
     * 检查去程时间段（早上6-11点）
     * 
     * @param itinerary 行程
     * @return 是否在去程首选时间段
     */
    private boolean checkOutboundTimeSlot(Itinerary itinerary) {
        if (itinerary == null || itinerary.getSegments() == null || itinerary.getSegments().length == 0) {
            return false;
        }
        
        // 取第一个航段的出发时间
        var firstSegment = itinerary.getSegments()[0];
        if (firstSegment.getDeparture() == null || firstSegment.getDeparture().getAt() == null) {
            return false;
        }
        
        LocalTime departureTime = parseTimeFromDateTime(firstSegment.getDeparture().getAt());
        return departureTime != null && 
               !departureTime.isBefore(MORNING_START) && 
               !departureTime.isAfter(MORNING_END);
    }
    
    /**
     * 检查返程时间段（17-22点）
     * 
     * @param itinerary 行程
     * @return 是否在返程首选时间段
     */
    private boolean checkInboundTimeSlot(Itinerary itinerary) {
        if (itinerary == null || itinerary.getSegments() == null || itinerary.getSegments().length == 0) {
            return false;
        }
        
        // 取第一个航段的出发时间（对于返程航班，这是从目的地出发的时间）
        var firstSegment = itinerary.getSegments()[0];
        if (firstSegment.getDeparture() == null || firstSegment.getDeparture().getAt() == null) {
            return false;
        }
        
        LocalTime departureTime = parseTimeFromDateTime(firstSegment.getDeparture().getAt());
        return departureTime != null && 
               !departureTime.isBefore(EVENING_START) && 
               !departureTime.isAfter(EVENING_END);
    }
    
    /**
     * 从日期时间字符串中解析时间
     * 
     * @param dateTimeStr 日期时间字符串（ISO格式）
     * @return 解析出的时间，解析失败返回null
     */
    private LocalTime parseTimeFromDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试解析ISO_LOCAL_DATE_TIME格式
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dateTime.toLocalTime();
        } catch (Exception e) {
            try {
                // 尝试解析ISO_OFFSET_DATE_TIME格式（带时区）
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return offsetDateTime.toLocalTime();
            } catch (Exception e2) {
                log.warn("Failed to parse departure/arrival time: {}", dateTimeStr);
                return null;
            }
        }
    }
    
    /**
     * 转换为FlightInfo
     */
    private FlightInfo convertToFlightInfo(FlightOfferSearch offer) {
        FlightInfo flightInfo = new FlightInfo();
        flightInfo.setOneWay(offer.isOneWay());
        flightInfo.setTotal(offer.getPrice().getTotal());
        flightInfo.setCurrency(offer.getPrice().getCurrency());
        List<ItineraryInfo> itineraries = new ArrayList<>();
        if (offer.getItineraries() != null) {
            for (var itinerary : offer.getItineraries()) {
                ItineraryInfo itineraryInfo = new ItineraryInfo();
                itineraryInfo.setDuration(itinerary.getDuration());
                List<SegmentInfo> segments = new ArrayList<>();
                
                if (itinerary.getSegments() != null) {
                    for (SearchSegment segment : itinerary.getSegments()) {
                        SegmentInfo segmentInfo = new SegmentInfo();
                        segmentInfo.setDeparture(segment.getDeparture().getIataCode());
                        segmentInfo.setDepartureTerminal(segment.getDeparture().getTerminal());
                        segmentInfo.setDepartureTime(segment.getDeparture().getAt());
                        segmentInfo.setArrival(segment.getArrival().getIataCode());
                        segmentInfo.setArrivalTerminal(segment.getArrival().getTerminal());
                        segmentInfo.setArrivalTime(segment.getArrival().getAt());
                        segmentInfo.setCarrierCode(segment.getCarrierCode());
                        segmentInfo.setNumber(segment.getNumber());
                        segmentInfo.setDuration(segmentInfo.getDuration());
                        segments.add(segmentInfo);
                    }
                }
                
                itineraryInfo.setSegments(segments);
                itineraries.add(itineraryInfo);
            }
        }
        
        flightInfo.setItineraries(itineraries);
        return flightInfo;
    }
    
    /**
     * 转换日期格式
     */
    private LocalDate convertToLocalDate(Object date) {
        if (date == null) {
            return null;
        }
        
        if (date instanceof java.util.Date) {
            return ((java.util.Date) date).toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        }
        
        // 其他日期格式的转换逻辑
        return LocalDate.now(); // 简化实现
    }
}
