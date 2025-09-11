package com.pkfare.trip.scale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;

import java.util.concurrent.Callable;

/**
 * 异步Callable处理超时拦截器
 * 
 * @author Trip Scale Team
 */
@Slf4j
public class AsyncTimeoutCallableProcessingInterceptor implements CallableProcessingInterceptor {

    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, Callable<T> task) {
        log.debug("Starting async callable processing for request: {}", request.getDescription(false));
    }

    @Override
    public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) {
        log.warn("Async callable processing timed out for request: {}", request.getDescription(false));
        return null; // 返回null表示使用默认超时处理
    }

    @Override
    public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
        log.debug("Async callable processing completed for request: {}", request.getDescription(false));
    }
}
