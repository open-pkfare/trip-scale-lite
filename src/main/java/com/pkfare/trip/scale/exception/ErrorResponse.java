package com.pkfare.trip.scale.exception;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 错误响应实体
 * 
 * @author Trip Scale Team
 */
@Data
public class ErrorResponse {
    
    /**
     * 错误码
     */
    private String errorCode;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 请求路径
     */
    private String path;
    
    public ErrorResponse(String errorCode, String errorMessage, String path) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}
