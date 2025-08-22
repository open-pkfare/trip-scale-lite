package com.pkfare.trip.scale.exception;

import java.math.BigDecimal;

/**
 * 预算超支异常类
 * 
 * @author Trip Scale Team
 */
public class BudgetExceededException extends TripPlanException {
    
    private BigDecimal actualCost;
    private BigDecimal budgetLimit;
    
    public BudgetExceededException(String errorCode, String errorMessage, BigDecimal actualCost, BigDecimal budgetLimit) {
        super(errorCode, errorMessage);
        this.actualCost = actualCost;
        this.budgetLimit = budgetLimit;
    }
    
    public BigDecimal getActualCost() {
        return actualCost;
    }
    
    public BigDecimal getBudgetLimit() {
        return budgetLimit;
    }
}
