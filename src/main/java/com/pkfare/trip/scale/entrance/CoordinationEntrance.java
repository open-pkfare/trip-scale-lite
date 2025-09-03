package com.pkfare.trip.scale.entrance;

import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.adk.web.config.DevConfig;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Content.Builder;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.function.AppRunner;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class CoordinationEntrance {

  @Autowired
  private AppRunner runner;

  @Autowired
  private DevConfig devConfig;

  /**
   * chat with agents
   * @param conversation
   * @return
   */
  public List<RespConversation> chat(List<byte[]> files, Conversation conversation) {
    Session session = devConfig.getSession(conversation.getConversationId(), conversation.getUserId());

    log.info("current session {} {}", session.id(), session.state().entrySet());

    Builder builder = Content.builder();
    List<Part> parts = Lists.newArrayList();
    if (StringUtils.isNotEmpty(conversation.getContent())){
      parts.add(Part.fromText(conversation.getContent()));
    }
    if (!CollectionUtils.isEmpty(files)){
      parts.addAll(files.stream().map(str-> Part.fromBytes(str,"jpeg")).toList());
    }
    Content userMsg = builder.parts(parts).build();
    Flowable<Event> events = runner.runAsync(conversation.getUserId(), session.id(), userMsg);
    StringBuilder stringBuilder = new StringBuilder();
    events.filter(UserEventFilter.instance()).blockingForEach(event -> {
//      setDone(event, conversation.getUserId(), conversation.getConversationId());
      log.info("event {}", event);
      if (event.content().isPresent()) {
        Content content = event.content().get();
        stringBuilder.append(content.text());
      }
    });

    RespConversation respConversation = new RespConversation();
    respConversation.setType("string");
    respConversation.setContent(stringBuilder.toString());
    respConversation.setConversationId(conversation.getConversationId());

    List<RespConversation> respConversations = Lists.newArrayList();
    respConversations.add(respConversation);
    return respConversations;
  }

}
