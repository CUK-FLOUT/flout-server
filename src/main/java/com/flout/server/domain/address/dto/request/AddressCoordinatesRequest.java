package com.flout.server.domain.address.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressCoordinatesRequest(
    @NotBlank(message = "주소를 입력해주세요.") @Size(max = 255) String address) {}
