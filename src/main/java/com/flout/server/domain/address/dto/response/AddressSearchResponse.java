package com.flout.server.domain.address.dto.response;

import java.util.List;

public record AddressSearchResponse(
    List<AddressSearchResult> addresses,
    int currentPage,
    int countPerPage,
    boolean hasMore,
    Integer nextPage) {
  public record AddressSearchResult(
      String roadAddress, String jibunAddress, String buildingName, String zipCode) {}
}
