package com.pkfare.trip.scale.api.amadeus.flightdates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.api.amadeus.authenticate.AmadeusAuthenticateApi;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusApiConfig;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusAuthenticateConfig;
import com.pkfare.trip.scale.api.amadeus.flightdates.request.FlightDatesRequest;
import com.pkfare.trip.scale.api.amadeus.flightdates.response.FlightDatesResponse;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * https://developers.amadeus.com/self-service/category/flights/api-doc/flight-cheapest-date-search/api-reference
 */
public class AmadeusFlightDatesAPI1 {

  private static final String FLIGHT_DATES_ENDPOINT = "/shopping/flight-dates";

  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AmadeusFlightDatesAPI1() {
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * 搜索最便宜的航班日期
   */
  public FlightDatesResponse searchCheapestFlightDates(FlightDatesRequest request) throws IOException {
    AmadeusAuthenticateApi authenticateApi = new AmadeusAuthenticateApi(httpClient, objectMapper);
    String accessToken = authenticateApi.authenticate(AmadeusAuthenticateConfig.getClientKey(), AmadeusAuthenticateConfig.getClientSecret());
    // 验证是否成功获取到access token（如果未获取到，抛出异常）
    if (accessToken == null) {
      throw new IllegalStateException("Not authenticated. Call authenticate() first.");
    }

    // 构建查询参数
    HttpUrl.Builder urlBuilder = HttpUrl.parse(AmadeusApiConfig.getBaseUrl() + FLIGHT_DATES_ENDPOINT).newBuilder();

    // 必需参数
    urlBuilder.addQueryParameter("origin", request.getOrigin());
    urlBuilder.addQueryParameter("destination", request.getDestination());
    urlBuilder.addQueryParameter("departureDate", request.getDepartureDate());

    // 可选参数
    if (request.getReturnDate() != null) {
      urlBuilder.addQueryParameter("returnDate", request.getReturnDate());
    }
    if (request.getCurrency() != null) {
      urlBuilder.addQueryParameter("currency", request.getCurrency());
    }
    if (request.getMax() != null) {
      urlBuilder.addQueryParameter("max", request.getMax().toString());
    }
    if (request.getNonStop() != null) {
      urlBuilder.addQueryParameter("nonStop", request.getNonStop().toString());
    }
    if (request.getOneWay() != null) {
      urlBuilder.addQueryParameter("oneWay", request.getOneWay().toString());
    }

    Request httpRequest = new Request.Builder()
        .url(urlBuilder.build())
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .get()
        .build();

    try (Response response = httpClient.newCall(httpRequest).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        return objectMapper.readValue(responseBody, FlightDatesResponse.class);
      } else {
        throw new IOException("AmadeusFlightDatesAPI call failed: " + response.code() + " - " + response.message());
      }
    }
  }


  /**
   * 关闭HTTP客户端
   */
  public void close() {
    if (httpClient != null) {
      httpClient.dispatcher().executorService().shutdown();
      httpClient.connectionPool().evictAll();
    }
  }

}
