package com.pkfare.trip.scale.entrance;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.pkfare.trip.scale.agent.orchestration.AnotherRootAgent;
import com.pkfare.trip.scale.agent.orchestration.RootAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
public class TripScaleController {

  private static final Logger logger = LoggerFactory.getLogger(TripScaleController.class);

  @Autowired
  private CoordinationEntrance coordinationEntrance;

  @RequestMapping(value = "/chat")
  @ResponseBody
  public List<RespConversation> chat(@RequestParam("file1") MultipartFile file1, @RequestParam("file2") MultipartFile file2,
      @RequestParam("file3") MultipartFile file3, @RequestParam("conversation") String conversationStr) {
    Conversation conversation = JSON.parseObject(conversationStr, Conversation.class);
    logger.info("收到聊天请求，会话ID: {}", conversation.getConversationId());
    if (StringUtils.isEmpty(conversation.getConversationId())) {
      conversation.setConversationId(UUID.randomUUID().toString());
      logger.info("生成新的会话ID: {}", conversation.getConversationId());
    }
    List<byte[]> files = Lists.newArrayList();
    try {
      if (!file1.isEmpty()){
        files.add(file1.getBytes());
      }
      if (!file2.isEmpty()){
        files.add(file2.getBytes());
      }
      if (!file3.isEmpty()){
        files.add(file3.getBytes());
      }
    }catch (Throwable e){
      log.info("file read error {}", ExceptionUtils.getStackTrace(e));
    }
    return coordinationEntrance.chat(files, conversation);
  }
}
