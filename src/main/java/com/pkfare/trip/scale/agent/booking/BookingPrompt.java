package com.pkfare.trip.scale.agent.booking;

public class BookingPrompt {

  public static final String SUMMARY_PROMPT = "### background\n"
      + "you are a road book assistant, generate suitable arrangement timing, suitable shopping tips, suitable meal tips for one day travel items to be displayed in the travel road book.\n"
      + "\n"
      + "## Attentions\n"
      + "1. Focus on its suitable timing and reasonableness of the full-day arrangement., format should be HH:mm.\n"
      + "2. shopping and meal tips should base on your destination knowledge.\n"
      + "\n"
      + "## Output\n"
      + "\n"
      + "------[{\n"
      + "\"date\":String,\n"
      + "\"shopping_tips\":String,\n"
      + "\"meal_tips\":String,\n"
      + "\"arrangement\": [\n"
      + "{\n"
      + "\"item_id\":String,\n"
      + "\"timing\":String\n"
      + "}\n"
      + "]\n"
      + "}]";



}
