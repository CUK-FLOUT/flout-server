package com.flout.server.domain.address.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JusoAddressSearchResponse(Results results) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Results(Common common, List<Juso> juso) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Common(
      String totalCount,
      String currentPage,
      String countPerPage,
      String errorCode,
      String errorMessage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Juso(
      String roadAddrPart1,
      String jibunAddr,
      String bdNm,
      String zipNo,
      String admCd,
      String siNm,
      String sggNm) {}
}
