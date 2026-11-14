package com.flout.server.domain.address.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AddressSearchResponse(
    List<AddressSearchResult> addresses,
    int currentPage,
    @Schema(description = "외부 주소 검색 페이지당 요청 건수", example = "10") int countPerPage,
    @Schema(description = "외부 검색 결과에 다음 페이지가 있는지 여부") boolean hasMore,
    @Schema(description = "다음 페이지 번호. 마지막 페이지는 null", nullable = true, example = "2")
        Integer nextPage) {
  public record AddressSearchResult(
      @Schema(description = "도로명 주소", example = "경기도 부천시 원미구 지봉로 43") String roadAddress,
      @Schema(description = "지번 주소") String jibunAddress,
      @Schema(description = "건물명") String buildingName,
      @Schema(description = "우편번호", example = "14662") String zipCode) {}
}
