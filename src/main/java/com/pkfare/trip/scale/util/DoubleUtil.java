package com.pkfare.trip.scale.util;

/**
 * Double工具类
 * 
 * @author Trip Scale Team
 */
public class DoubleUtil {
    
    /**
     * 字符串转Double
     * 
     * @param str 字符串
     * @return Double值，转换失败返回0.0
     */
    public static double strToDouble(String str) {
        if (str == null || str.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Object转Double
     * 
     * @param obj 对象
     * @return Double值，转换失败返回0.0
     */
    public static double objToDouble(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        
        if (obj instanceof Double) {
            return (Double) obj;
        }
        
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        
        return strToDouble(obj.toString());
    }
}