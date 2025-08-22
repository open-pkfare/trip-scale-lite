package com.pkfare.trip.scale.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 日期工具类
 * 
 * @author Trip Scale Team
 */
public class DateUtil {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * 字符串转LocalDate
     * 
     * @param dateStr 日期字符串，格式：yyyy-MM-dd
     * @return LocalDate
     */
    public static LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }
    
    /**
     * LocalDate转字符串
     * 
     * @param date LocalDate
     * @return 日期字符串，格式：yyyy-MM-dd
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * 计算两个日期之间的天数差
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 天数差
     */
    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
    
    /**
     * 日期加天数
     * 
     * @param date 基准日期
     * @param days 要加的天数
     * @return 新日期
     */
    public static LocalDate addDays(LocalDate date, long days) {
        return date.plusDays(days);
    }
    
    /**
     * 日期减天数
     * 
     * @param date 基准日期
     * @param days 要减的天数
     * @return 新日期
     */
    public static LocalDate minusDays(LocalDate date, long days) {
        return date.minusDays(days);
    }
    
    /**
     * 构建日期范围字符串，用于Amadeus API
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期范围字符串，格式：yyyy-MM-dd,yyyy-MM-dd
     */
    public static String buildDateRange(LocalDate startDate, LocalDate endDate) {
        return formatDate(startDate) + "," + formatDate(endDate);
    }
}
