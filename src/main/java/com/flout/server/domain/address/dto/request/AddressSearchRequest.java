package com.flout.server.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressSearchRequest(
    @NotBlank(message = "검색어를 입력해주세요.") @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") @Schema(description = "도로명 또는 지번 주소 검색어", example = "경기도 부천시 원미구 지봉로 43")
        String keyword) {}
