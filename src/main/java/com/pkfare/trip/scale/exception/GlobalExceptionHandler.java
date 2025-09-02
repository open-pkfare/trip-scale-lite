package com.pkfare.trip.scale.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * 全局异常处理器
 * 
 * @author Trip Scale Team
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理旅行计划异常
     */
    @ExceptionHandler(TripPlanException.class)
    public ResponseEntity<ErrorResponse> handleTripPlanException(TripPlanException e, WebRequest request) {
        log.error("TripPlanException occurred: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
            e.getErrorCode(),
            e.getErrorMessage(),
            request.getDescription(false)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * 处理外部API异常
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException e, WebRequest request) {
        log.error("ExternalApiException occurred: API={}, Status={}, Message={}", 
                 e.getApiName(), e.getHttpStatus(), e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
            e.getErrorCode(),
            e.getErrorMessage(),
            request.getDescription(false)
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(ParameterValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidationException(ParameterValidationException e, WebRequest request) {
        log.error("ParameterValidationException occurred: Field={}, Value={}, Message={}", 
                 e.getFieldName(), e.getFieldValue(), e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
            e.getErrorCode(),
            e.getErrorMessage(),
            request.getDescription(false)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * 处理预算超支异常
     */
    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<ErrorResponse> handleBudgetExceededException(BudgetExceededException e, WebRequest request) {
        log.error("BudgetExceededException occurred: ActualCost={}, BudgetLimit={}, Message={}", 
                 e.getActualCost(), e.getBudgetLimit(), e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
            e.getErrorCode(),
            e.getErrorMessage(),
            request.getDescription(false)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e, WebRequest request) {
        log.error("Unexpected exception occurred: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getDescription(false)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
