package com.pkfare.trip.scale.util;

/**
 * 位置工具类
 * 
 * @author Trip Scale Team
 */
public class LocationUtil {
    
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    /**
     * 计算两个经纬度点之间的距离（公里）
     * 使用Haversine公式
     * 
     * @param lat1 纬度1
     * @param lon1 经度1
     * @param lat2 纬度2
     * @param lon2 经度2
     * @return 距离（公里）
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * 检查活动是否在酒店指定半径范围内
     * 
     * @param hotelLat 酒店纬度
     * @param hotelLon 酒店经度
     * @param activityLat 活动纬度
     * @param activityLon 活动经度
     * @param radiusKm 半径（公里）
     * @return 是否在范围内
     */
    public static boolean isWithinRadius(double hotelLat, double hotelLon, 
                                       double activityLat, double activityLon, 
                                       double radiusKm) {
        double distance = calculateDistance(hotelLat, hotelLon, activityLat, activityLon);
        return distance <= radiusKm;
    }
}
