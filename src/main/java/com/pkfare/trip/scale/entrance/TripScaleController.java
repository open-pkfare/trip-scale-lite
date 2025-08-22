package com.pkfare.trip.scale.entrance;

import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.agent.orchestration.RootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripScaleController {

  private static final Logger logger = LoggerFactory.getLogger(TripScaleController.class);

  @Autowired
  private RootAgent rootAgent;

  @Autowired
  private CoordinationEntrance coordinationEntrance;

  @RequestMapping("/chat")
  @ResponseBody
  public List<RespConversation> chat(@RequestBody Conversation conversation) {
    logger.info("收到聊天请求，会话ID: {}", conversation.getConversationId());
    if (StringUtils.isEmpty(conversation.getConversationId())) {
      conversation.setConversationId(UUID.randomUUID().toString());
      logger.info("生成新的会话ID: {}", conversation.getConversationId());
    }
    return coordinationEntrance.chat(conversation);
  }
}
