package com.pkfare.trip.scale.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价格工具类
 * 
 * @author Trip Scale Team
 */
public class PriceUtil {
    
    /**
     * 字符串转BigDecimal
     * 
     * @param priceStr 价格字符串
     * @return BigDecimal
     */
    public static BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(priceStr.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 字符串转BigDecimal
     *
     * @param priceStr 价格字符串
     * @return BigDecimal
     */
    public static BigDecimal parsePrice(Double priceStr) {
        if (priceStr == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(priceStr);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    
    /**
     * 价格除法运算，保留2位小数
     * 
     * @param dividend 被除数
     * @param divisor 除数
     * @return 结果
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(divisor, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 价格加法运算
     * 
     * @param price1 价格1
     * @param price2 价格2
     * @return 结果
     */
    public static BigDecimal add(BigDecimal price1, BigDecimal price2) {
        return price1.add(price2);
    }
    
    /**
     * 比较两个价格
     * 
     * @param price1 价格1
     * @param price2 价格2
     * @return price1 < price2 返回-1，price1 = price2 返回0，price1 > price2 返回1
     */
    public static int compare(BigDecimal price1, BigDecimal price2) {
        return price1.compareTo(price2);
    }
    
    /**
     * 格式化价格为字符串，保留2位小数
     * 
     * @param price 价格
     * @return 格式化后的价格字符串
     */
    public static String formatPrice(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
