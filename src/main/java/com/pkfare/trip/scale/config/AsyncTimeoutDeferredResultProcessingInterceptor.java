package com.pkfare.trip.scale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;

/**
 * 异步DeferredResult处理超时拦截器
 * 
 * @author Trip Scale Team
 */
@Slf4j
public class AsyncTimeoutDeferredResultProcessingInterceptor implements DeferredResultProcessingInterceptor {

    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.debug("Starting async deferred result processing for request: {}", request.getDescription(false));
    }

    @Override
    public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.warn("Async deferred result processing timed out for request: {}", request.getDescription(false));
        
        // 设置超时响应
        deferredResult.setErrorResult(new RuntimeException("Request processing timed out"));
        return true; // 表示已处理超时
    }

    @Override
    public <T> void afterCompletion(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.debug("Async deferred result processing completed for request: {}", request.getDescription(false));
    }
}
