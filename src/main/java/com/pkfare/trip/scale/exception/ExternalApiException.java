package com.pkfare.trip.scale.exception;

/**
 * 外部API异常类
 * 
 * @author Trip Scale Team
 */
public class ExternalApiException extends TripPlanException {
    
    private int httpStatus;
    private String apiName;
    
    public ExternalApiException(String errorCode, String errorMessage, int httpStatus, String apiName) {
        super(errorCode, errorMessage);
        this.httpStatus = httpStatus;
        this.apiName = apiName;
    }
    
    public ExternalApiException(String errorCode, String errorMessage, int httpStatus, String apiName, Throwable cause) {
        super(errorCode, errorMessage, cause);
        this.httpStatus = httpStatus;
        this.apiName = apiName;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public String getApiName() {
        return apiName;
    }
}
