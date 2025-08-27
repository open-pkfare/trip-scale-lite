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
}
