package com.pkfare.trip.scale;

import com.pkfare.trip.scale.dto.Conversation;
import com.pkfare.trip.scale.dto.RespConversation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripScaleController {

    @GetMapping("/chat")
    public RespConversation chat(Conversation conversation) {
    }
}
