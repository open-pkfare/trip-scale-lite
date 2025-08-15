package com.pkfare.trip.scale.api.amadeus.authenticate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkfare.trip.scale.api.amadeus.config.AmadeusApiConfig;
import com.pkfare.trip.scale.api.amadeus.flightdates.TokenResponse;
import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AmadeusAuthenticateApi {

  private static final String AUTHENTICATE_ENDPOINT = "/security/oauth2/token";

  private OkHttpClient httpClient;
  private ObjectMapper objectMapper;


  public AmadeusAuthenticateApi(OkHttpClient httpClient,ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }


  /**
   * 获取访问令牌
   */
  public String authenticate(String clientId, String clientSecret) throws IOException {
    String credentials = okhttp3.Credentials.basic(clientId, clientSecret);

    RequestBody formBody = new FormBody.Builder()
        .add("grant_type", "client_credentials")
        .build();

    Request request = new Request.Builder()
        .url(AmadeusApiConfig.getBaseUrl() + AUTHENTICATE_ENDPOINT)
        .post(formBody)
        .header("Authorization", credentials)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        TokenResponse tokenResponse = objectMapper.readValue(responseBody, TokenResponse.class);
        String accessToken = tokenResponse.getAccessToken();
        return accessToken;
      } else {
        throw new IOException("Authentication failed: " + response.code());
      }
    }
  }

}
