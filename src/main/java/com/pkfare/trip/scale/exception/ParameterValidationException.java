package com.pkfare.trip.scale.exception;

/**
 * 参数验证异常类
 * 
 * @author Trip Scale Team
 */
public class ParameterValidationException extends TripPlanException {
    
    private String fieldName;
    private Object fieldValue;
    
    public ParameterValidationException(String errorCode, String errorMessage, String fieldName, Object fieldValue) {
        super(errorCode, errorMessage);
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public Object getFieldValue() {
        return fieldValue;
    }
}
