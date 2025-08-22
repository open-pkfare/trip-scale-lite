package com.pkfare.trip.scale.dto;

import lombok.Data;

@Data
public class RespConversation {
  private String type;
  private Object data;
  private String content;
  private String conversationId;

}