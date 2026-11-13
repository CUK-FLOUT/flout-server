package com.flout.server.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AddressSearchRequest(
    @NotBlank(message = "검색어를 입력해주세요.") @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") @Schema(description = "주소 검색어 (최대 100자)", example = "경기도 부천시 원미구 지봉로 43")
        String keyword,
    @Min(1) @Schema(
            description = "페이지 번호",
            defaultValue = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer currentPage) {
  public AddressSearchRequest {
    if (currentPage == null) currentPage = 1;
  }
}
