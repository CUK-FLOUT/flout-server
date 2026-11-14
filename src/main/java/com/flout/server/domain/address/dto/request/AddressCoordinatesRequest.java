package com.flout.server.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressCoordinatesRequest(
    @NotBlank(message = "주소를 입력해주세요.") @Size(max = 255) @Schema(description = "부천시 원미구·소사구·오정구의 건물번호가 포함된 주소", example = "경기도 부천시 원미구 지봉로 43")
        String address) {}
