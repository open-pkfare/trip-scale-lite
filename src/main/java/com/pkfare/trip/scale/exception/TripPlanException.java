package com.pkfare.trip.scale.exception;

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
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
