package com.flout.server.domain.address.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AddressSearchResponse(
    @Schema(description = "주소 검색 결과 목록") List<AddressSearchResult> addresses) {
  public record AddressSearchResult(
      @Schema(description = "도로명 주소", example = "경기도 부천시 원미구 지봉로 43") String roadAddress,
      @Schema(description = "지번 주소", example = "경기 부천시 원미구 역곡동 산 43-1") String jibunAddress,
      @Schema(description = "건물명", example = "가톨릭대학교 성심교정") String buildingName,
      @Schema(description = "우편번호", example = "14662") String zipCode,
      @Schema(description = "위도", example = "37.4874849962478") Double latitude,
      @Schema(description = "경도", example = "126.79856716311") Double longitude) {}
}
