package com.flout.server.domain.address.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressSearchResponse(List<Document> documents) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Document(
      @JsonProperty("address_name") String addressName,
      String x,
      String y,
      @JsonProperty("address") Address address,
      @JsonProperty("road_address") RoadAddress roadAddress) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Address(@JsonProperty("address_name") String addressName) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RoadAddress(
      @JsonProperty("address_name") String addressName,
      @JsonProperty("building_name") String buildingName,
      @JsonProperty("zone_no") String zoneNo) {}
}
