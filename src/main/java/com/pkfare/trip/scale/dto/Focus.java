package com.pkfare.trip.scale.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Focus {

    private LocalDateTime timestamp;
    private String content;

}
