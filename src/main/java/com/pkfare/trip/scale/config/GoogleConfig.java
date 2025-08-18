package com.pkfare.trip.scale.config;

import com.google.adk.models.Gemini;

public class GoogleConfig {

  public static String GOOGLE_API_KEY = "AIzaSyAl16sCWICrc0OTJ1FxfpH5EdkQHHLHA8A";

  public static Gemini GEMINI_2_5_PRO = new Gemini("gemini-2.5-pro", GoogleConfig.GOOGLE_API_KEY);

}
