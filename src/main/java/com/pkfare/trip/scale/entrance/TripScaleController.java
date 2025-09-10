package com.pkfare.trip.scale.entrance;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.pkfare.trip.scale.agent.booking.BookingAgent;
import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
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
  public List<RespConversation> chat(@RequestParam(value = "file1", required = false) MultipartFile file1,
      @RequestParam(value = "file2", required = false) MultipartFile file2,
      @RequestParam(value = "file3", required = false) MultipartFile file3,
      @RequestParam("conversation") String conversationStr) {
    Conversation conversation = JSON.parseObject(conversationStr, Conversation.class);
    logger.info("receive conversation ID: {}", conversation.getConversationId());
    if (StringUtils.isEmpty(conversation.getConversationId())) {
      conversation.setConversationId(UUID.randomUUID().toString());
      logger.info("generate new conversation ID: {}", conversation.getConversationId());
    }
    List<Pair<String,byte[]>> files = Lists.newArrayList();
    try {
      if (null != file1 && !file1.isEmpty()) {
        files.add(Pair.of(file1.getContentType(),file1.getBytes()));
      }
      if (null != file2 && !file2.isEmpty()) {
        files.add(Pair.of(file2.getContentType(),file2.getBytes()));
      }
      if (null != file3 && !file3.isEmpty()) {
        files.add(Pair.of(file3.getContentType(),file3.getBytes()));
      }
    } catch (Throwable e) {
      log.info("file read error {}", ExceptionUtils.getStackTrace(e));
    }
    return coordinationEntrance.chat(files, conversation);
  }

  @RequestMapping("/roadbook/{id}")
  public String roadbook(@PathVariable("id") String id){
    JSON json= BookingAgent.instance().roadbook(id);
    if (json == null) {
      return "not exist roadbook id "+ id;
    }
    return BookingAgent.instance().roadbook(id).toString();
  }
}
