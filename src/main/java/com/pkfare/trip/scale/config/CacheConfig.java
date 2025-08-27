package com.pkfare.trip.scale.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 缓存配置类
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Configuration
public class CacheConfig {

    @Value("${trip.plan.cache.flight-dates.expire-after-write-minutes:120}")
    private int flightDatesExpireMinutes;

    @Value("${trip.plan.cache.flight-dates.maximum-size:1000}")
    private int flightDatesMaximumSize;

    @Value("${trip.plan.cache.flight-dates.initial-capacity:100}")
    private int flightDatesInitialCapacity;

    @Value("${trip.plan.cache.flight-offers.expire-after-write-minutes:120}")
    private int flightOffersExpireMinutes;

    @Value("${trip.plan.cache.flight-offers.maximum-size:1000}")
    private int flightOffersMaximumSize;

    @Value("${trip.plan.cache.flight-offers.initial-capacity:100}")
    private int flightOffersInitialCapacity;

    @Value("${trip.plan.cache.hotel-offers.expire-after-write-minutes:120}")
    private int hotelOffersExpireMinutes;

    @Value("${trip.plan.cache.hotel-offers.maximum-size:1000}")
    private int hotelOffersMaximumSize;

    @Value("${trip.plan.cache.hotel-offers.initial-capacity:100}")
    private int hotelOffersInitialCapacity;

    /**
     * 航班日期缓存
     * 
     * @return Caffeine缓存实例
     */
    @Bean("flightDatesCache")
    public Cache<String, Object> flightDatesCache() {
        log.info("Creating flight dates cache with expireAfterWrite: {} minutes, maximumSize: {}, initialCapacity: {}",
                flightDatesExpireMinutes, flightDatesMaximumSize, flightDatesInitialCapacity);

        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(flightDatesExpireMinutes))
                .maximumSize(flightDatesMaximumSize)
                .initialCapacity(flightDatesInitialCapacity)
                .recordStats() // 启用统计信息
                .removalListener((key, value, cause) -> {
                    log.debug("Cache entry removed - key: {}, cause: {}", key, cause);
                })
                .build();
    }

    /**
     * 航班报价缓存
     * 
     * @return Caffeine缓存实例
     */
    @Bean("flightOffersCache")
    public Cache<String, Object> flightOffersCache() {
        log.info("Creating flight offers cache with expireAfterWrite: {} minutes, maximumSize: {}, initialCapacity: {}",
                flightOffersExpireMinutes, flightOffersMaximumSize, flightOffersInitialCapacity);

        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(flightOffersExpireMinutes))
                .maximumSize(flightOffersMaximumSize)
                .initialCapacity(flightOffersInitialCapacity)
                .recordStats() // 启用统计信息
                .removalListener((key, value, cause) -> {
                    log.debug("Flight offers cache entry removed - key: {}, cause: {}", key, cause);
                })
                .build();
    }

    /**
     * 酒店报价缓存
     * 
     * @return Caffeine缓存实例
     */
    @Bean("hotelOffersCache")
    public Cache<String, Object> hotelOffersCache() {
        log.info("Creating hotel offers cache with expireAfterWrite: {} minutes, maximumSize: {}, initialCapacity: {}",
                hotelOffersExpireMinutes, hotelOffersMaximumSize, hotelOffersInitialCapacity);

        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(hotelOffersExpireMinutes))
                .maximumSize(hotelOffersMaximumSize)
                .initialCapacity(hotelOffersInitialCapacity)
                .recordStats() // 启用统计信息
                .removalListener((key, value, cause) -> {
                    log.debug("Hotel offers cache entry removed - key: {}, cause: {}", key, cause);
                })
                .build();
    }
}
