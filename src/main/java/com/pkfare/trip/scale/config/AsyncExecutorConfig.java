package com.pkfare.trip.scale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步执行器配置
 * 为旅行计划生成提供自定义线程池
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    @Value("${trip.plan.async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${trip.plan.async.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${trip.plan.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${trip.plan.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${trip.plan.async.thread-name-prefix:TripPlan-}")
    private String threadNamePrefix;

    /**
     * 旅行计划生成专用线程池
     * 
     * @return 自定义线程池执行器
     */
    @Bean("tripPlanExecutor")
    public Executor tripPlanExecutor() {
        log.info("Creating custom thread pool for trip plan generation: " +
                "corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}", 
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new TripPlanThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );

        // 允许核心线程超时
        executor.allowCoreThreadTimeOut(true);
        
        return executor;
    }

    /**
     * 自定义线程工厂
     */
    private static class TripPlanThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        TripPlanThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
