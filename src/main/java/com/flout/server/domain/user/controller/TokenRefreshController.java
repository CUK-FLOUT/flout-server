package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.RefreshTokenRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.TokenRefreshService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증 토큰", description = "공통 인증 토큰 재발급")
public class TokenRefreshController {
  private final TokenRefreshService service;

  @PostMapping("/refresh")
  @Operation(
      summary = "인증 토큰 재발급",
      description = "본문의 Refresh Token으로 새 토큰 쌍을 발급합니다. Authorization 헤더는 필요하지 않습니다.")
  public SuccessResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return SuccessResponse.of(
        service.refresh(request.refreshToken()), UserSuccessCode.TOKEN_REFRESH_SUCCESS);
  }
}
