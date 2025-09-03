package com.pkfare.trip.scale.agent.booking;

public class BookingPrompt {

  public static final String SUMMARY_PROMPT = "### background\n"
      + "you are a road book assistant, generate summary of input items.\n"
      + "\n"
      + "## Attentions\n"
      + "1. Focus on its timing.\n"
      + "2. If it is a tour attraction or activity item, focus on feasible transportation way.\n"
      + "\n"
      + "## Output\n"
      + "{\n"
      + "    \"item_id\":String,\n"
      + "    \"summary\":String\n"
      + "}\n";

}
