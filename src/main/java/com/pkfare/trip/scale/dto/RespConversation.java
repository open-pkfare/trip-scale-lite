package com.pkfare.trip.scale.dto;

import lombok.Data;

@Data
public class RespConversation extends Conversation {
  private String type;
  private Object data;
}