package com.pkfare.trip.scale.util;

import com.pkfare.trip.scale.exception.ParameterValidationException;
import com.pkfare.trip.scale.plan.service.param.GeneratePlanParam;
import com.pkfare.trip.scale.plan.service.param.TripRouteParam;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 参数验证工具类
 * 
 * @author Trip Scale Team
 */
public class ValidationUtil {
    
    /**
     * 验证生成计划参数
     * 
     * @param param 生成计划参数
     * @throws ParameterValidationException 参数验证异常
     */
    public static void validateGeneratePlanParam(GeneratePlanParam param) {
        if (param == null) {
            throw new ParameterValidationException("PARAM_NULL", "GeneratePlanParam cannot be null", "param", null);
        }
        
        // 验证出发地
        if (StringUtils.isBlank(param.getOrigin())) {
            throw new ParameterValidationException("ORIGIN_EMPTY", "Origin cannot be empty", "origin", param.getOrigin());
        }
        
        // 验证日期
        if (StringUtils.isBlank(param.getStart_period())) {
            throw new ParameterValidationException("START_PERIOD_EMPTY", "Start period cannot be empty", "start_period", param.getStart_period());
        }
        
        if (StringUtils.isBlank(param.getEnd_period())) {
            throw new ParameterValidationException("END_PERIOD_EMPTY", "End period cannot be empty", "end_period", param.getEnd_period());
        }
        
        // 验证日期格式和逻辑
        try {
            LocalDate startDate = DateUtil.parseDate(param.getStart_period());
            LocalDate endDate = DateUtil.parseDate(param.getEnd_period());
            
            if (startDate.isAfter(endDate)) {
                throw new ParameterValidationException("INVALID_DATE_RANGE", "Start date must be before end date", 
                    "date_range", param.getStart_period() + " - " + param.getEnd_period());
            }
        } catch (Exception e) {
            throw new ParameterValidationException("INVALID_DATE_FORMAT", "Invalid date format, expected yyyy-MM-dd", 
                "date_format", param.getStart_period() + " - " + param.getEnd_period());
        }
        
        // 验证行程天数
        if (param.getTrip_days() <= 0) {
            throw new ParameterValidationException("INVALID_TRIP_DAYS", "Trip days must be greater than 0", "trip_days", param.getTrip_days());
        }
        
        // 验证人数
        if (param.getAdult_number() <= 0) {
            throw new ParameterValidationException("INVALID_ADULT_NUMBER", "Adult number must be greater than 0", "adult_number", param.getAdult_number());
        }
        
        if (param.getChild_number() < 0) {
            throw new ParameterValidationException("INVALID_CHILD_NUMBER", "Child number cannot be negative", "child_number", param.getChild_number());
        }
        
        // 验证预算
        if (StringUtils.isBlank(param.getBudgets())) {
            throw new ParameterValidationException("BUDGET_EMPTY", "Budget cannot be empty", "budgets", param.getBudgets());
        }
        
        try {
            BigDecimal budget = PriceUtil.parsePrice(param.getBudgets());
            if (budget.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ParameterValidationException("INVALID_BUDGET", "Budget must be greater than 0", "budgets", param.getBudgets());
            }
        } catch (Exception e) {
            throw new ParameterValidationException("INVALID_BUDGET_FORMAT", "Invalid budget format", "budgets", param.getBudgets());
        }
        
        // 验证币种
        if (StringUtils.isBlank(param.getCurrency())) {
            throw new ParameterValidationException("CURRENCY_EMPTY", "Currency cannot be empty", "currency", param.getCurrency());
        }
        
        // 验证房间数量
        if (param.getRoom_quantity() <= 0) {
            throw new ParameterValidationException("INVALID_ROOM_QUANTITY", "Room quantity must be greater than 0", "room_quantity", param.getRoom_quantity());
        }
        
        // 验证行程路线
        List<TripRouteParam> tripRoutes = param.getTrip_routes();
        if (tripRoutes == null || tripRoutes.isEmpty()) {
            throw new ParameterValidationException("TRIP_ROUTES_EMPTY", "Trip routes cannot be empty", "trip_routes", tripRoutes);
        }
        
        // 验证每个行程路线
        for (int i = 0; i < tripRoutes.size(); i++) {
            validateTripRouteParam(tripRoutes.get(i), i);
        }
        
        // 验证总停留天数是否匹配
        int totalStayDays = tripRoutes.stream().mapToInt(TripRouteParam::getStay_days).sum();
        if (totalStayDays != param.getTrip_days()) {
            throw new ParameterValidationException("STAY_DAYS_MISMATCH", 
                "Total stay days (" + totalStayDays + ") does not match trip days (" + param.getTrip_days() + ")", 
                "stay_days_total", totalStayDays);
        }
    }
    
    /**
     * 验证行程路线参数
     * 
     * @param routeParam 行程路线参数
     * @param index 索引
     * @throws ParameterValidationException 参数验证异常
     */
    private static void validateTripRouteParam(TripRouteParam routeParam, int index) {
        String prefix = "trip_routes[" + index + "]";
        
        if (routeParam == null) {
            throw new ParameterValidationException("TRIP_ROUTE_NULL", "Trip route cannot be null", prefix, null);
        }
        
        // 验证停留天数
        if (routeParam.getStay_days() <= 0) {
            throw new ParameterValidationException("INVALID_STAY_DAYS", "Stay days must be greater than 0", 
                prefix + ".stay_days", routeParam.getStay_days());
        }
        
        // 验证目的地城市
        if (StringUtils.isBlank(routeParam.getDestination_city())) {
            throw new ParameterValidationException("DESTINATION_CITY_EMPTY", "Destination city cannot be empty", 
                prefix + ".destination_city", routeParam.getDestination_city());
        }
        
        // 验证国家代码
        if (StringUtils.isBlank(routeParam.getCountry_code())) {
            throw new ParameterValidationException("COUNTRY_CODE_EMPTY", "Country code cannot be empty", 
                prefix + ".country_code", routeParam.getCountry_code());
        }
        
        // 验证位置代码
        if (StringUtils.isBlank(routeParam.getLocation_code())) {
            throw new ParameterValidationException("LOCATION_CODE_EMPTY", "Location code cannot be empty", 
                prefix + ".location_code", routeParam.getLocation_code());
        }
    }
}
