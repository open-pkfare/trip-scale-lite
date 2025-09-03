package com.pkfare.trip.scale.entrance;

import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.genai.types.Content;
import com.google.genai.types.Content.Builder;
import com.google.genai.types.Part;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import com.pkfare.trip.scale.function.UserEventFilter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class CoordinationEntrance {

  private static final String NAME = "tripScale";

  public static InMemoryRunner runner = new InMemoryRunner(AnotherRootAgent.instance());

  @PostConstruct
  public void init(){
    AnotherRootAgent.instance().setSessionService(runner.sessionService());
  }

  /**
   * chat with agents
   * @param conversation
   * @return
   */
  public List<RespConversation> chat(List<byte[]> files, Conversation conversation) {
    Session session = AnotherRootAgent.instance().getSession(conversation.getConversationId(), conversation.getUserId());

    log.info("current session {}", session.state().entrySet());

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
