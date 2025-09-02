package com.pkfare.trip.scale.api.amadeus.exception;


public class AmadeusApiException extends  RuntimeException{

  private int errorCode;

  private String errorMsg;

  public AmadeusApiException(int errorCode, String errorMsg) {
    this.errorCode = errorCode;
    this.errorMsg = errorMsg;
  }

  @Override
  public String toString() {
    return super.toString() + " errorCode:" + errorCode + "errorMsg:" + errorMsg ;
  }

}

