package com.pkfare.trip.scale;

import com.pkfare.trip.scale.agent.orchestration.RootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripScaleController {

  @Autowired
  private RootAgent rootAgent;

  @GetMapping("/chat")
  public RespConversation chat(Conversation conversation) {
    if (StringUtils.isEmpty(conversation.getConversationId())) {
      conversation.setConversationId(UUID.randomUUID().toString());
    }
    return rootAgent.chat(conversation);
  }
}
