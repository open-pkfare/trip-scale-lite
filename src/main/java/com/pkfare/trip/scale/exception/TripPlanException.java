package com.pkfare.trip.scale.exception;

import com.pkfare.trip.scale.model.enums.TripPlanErrorCodeEnum;

/**
 * 旅行计划基础异常类
 * 
 * @author Trip Scale Team
 */
public class TripPlanException extends RuntimeException {
    
    private String errorCode;
    private String errorMessage;
    
    public TripPlanException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public TripPlanException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 使用错误码枚举创建异常
     * @param errorCodeEnum 错误码枚举
     */
    public TripPlanException(TripPlanErrorCodeEnum errorCodeEnum) {
        super(errorCodeEnum.getMessage());
        this.errorCode = errorCodeEnum.getCode();
        this.errorMessage = errorCodeEnum.getMessage();
    }
    
    /**
     * 使用错误码枚举和原因创建异常
     * @param errorCodeEnum 错误码枚举
     * @param cause 异常原因
     */
    public TripPlanException(TripPlanErrorCodeEnum errorCodeEnum, Throwable cause) {
        super(errorCodeEnum.getMessage(), cause);
        this.errorCode = errorCodeEnum.getCode();
        this.errorMessage = errorCodeEnum.getMessage();
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
