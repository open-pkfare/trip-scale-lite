package com.pkfare.trip.scale.dto;

import java.util.List;
import lombok.Data;

@Data
public class Conversation {

  private String content;
  private List<String> files;
  private String conversationId;
  private String userId;
}
