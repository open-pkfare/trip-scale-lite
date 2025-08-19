package com.pkfare.trip.scale;

import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.agent.orchestration.RootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripScaleController {

  @Autowired
  private RootAgent rootAgent;

  @Autowired
  private AnotherRootAgent anotherRootAgent;

  @RequestMapping("/chat")
  @ResponseBody
  public List<RespConversation> chat(@RequestBody Conversation conversation) {
    if (StringUtils.isEmpty(conversation.getConversationId())) {
      conversation.setConversationId(UUID.randomUUID().toString());
    }
    return anotherRootAgent.chat(conversation);
  }
}
