package com.pkfare.trip.scale.api.amadeus.flightdates.response;

import io.opencensus.trace.export.SpanData.Links;
import lombok.Data;


@Data
public class Meta {
  private Integer count;
  private Links links;
}
