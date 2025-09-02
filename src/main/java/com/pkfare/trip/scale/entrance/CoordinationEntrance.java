package com.pkfare.trip.scale.entrance;

import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Content.Builder;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.function.AppRunner;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class CoordinationEntrance {

  private static final String NAME = "tripScale";

  @Autowired
  private AppRunner runner;

//  @PostConstruct
//  public void init(){
//    AnotherRootAgent.instance().setSessionService(runner.sessionService());
//  }

  /**
   * chat with agents
   * @param conversation
   * @return
   */
  public List<RespConversation> chat(Conversation conversation) {
    Session session = AnotherRootAgent.instance().getSession(conversation.getConversationId(), conversation.getUserId());
    log.info("current session {}", session.state().entrySet());
    Builder builder = Content.builder();
    List<Part> parts = Lists.newArrayList();
    if (StringUtils.isNotEmpty(conversation.getContent())){
      parts.add(Part.fromText(conversation.getContent()));
    }
    if (!CollectionUtils.isEmpty(conversation.getFiles())){
      parts.addAll(conversation.getFiles().stream().map(str-> Part.fromBytes(Base64.getDecoder().decode(str),"jpeg")).toList());
    }
    Content userMsg = builder.parts(parts).build();

    Flowable<Event> events = runner.runAsync(conversation.getUserId(), session.id(), userMsg);
//    StringBuilder stringBuilder = new StringBuilder();
    List<RespConversation> respConversations = Lists.newArrayList();

    Map<String, StringBuilder> map = Maps.newConcurrentMap();

    events.filter(UserEventFilter.instance()).blockingForEach(event -> {
//      setDone(event, conversation.getUserId(), conversation.getConversationId());
      log.info("event {}", event);

      if (event.content().isPresent()) {
        Content content = event.content().get();
        String role = "";
        if (content.role().isPresent()){
          role = content.role().get();
        }
        switch (role){
          case "planner":
            map.computeIfAbsent("object", k-> new StringBuilder()).append(content.text());
            break;
          default:
            map.computeIfAbsent("string", k-> new StringBuilder()).append(content.text());
            break;
        }
      }
    });

    map.forEach((key, value)-> {
      RespConversation respConversation = new RespConversation();
      respConversation.setType(key);
      respConversation.setContent(value.toString());
      respConversation.setConversationId(conversation.getConversationId());
      respConversations.add(respConversation);
    });

    log.info("state : {}", new Gson().toJson(session.state()));
    return respConversations;
  }


}
