package com.pkfare.trip.scale.function;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.functions.Predicate;

public class UserEventFilter implements Predicate<Event> {

  private static UserEventFilter USER_EVENT_FILTER = new UserEventFilter();

  public static UserEventFilter instance(){
    return USER_EVENT_FILTER;
  }

  @Override
  public boolean test(Event event) throws Throwable {
    if(event.content().isPresent()){
      Content content = event.content().get();
      String role = content.role().get();
      if ("user".equals(role)){
        return false;
      }
    }
    return true;
  }
}
