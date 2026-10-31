package com.flout.server.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AddressSearchRequest(
    @NotBlank(message = "검색어를 입력해주세요.") @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") String keyword,
    @Min(1) @Schema(
            description = "페이지 번호",
            defaultValue = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer currentPage) {
  public AddressSearchRequest {
    if (currentPage == null) currentPage = 1;
  }
}
