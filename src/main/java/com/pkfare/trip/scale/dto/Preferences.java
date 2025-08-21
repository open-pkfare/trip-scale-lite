package com.pkfare.trip.scale.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Preferences {

  private List<String> likes;
  private List<String> hates;
  private List<String> prefer;

}
