package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.RefreshTokenRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.TokenRefreshService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증/인가", description = "회원가입, 로그인, 토큰 재발급 및 운영자 계정 정보 조회")
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

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "로그아웃",
      description =
          "주민과 운영자가 공통으로 사용합니다. 본문의 리프레시 토큰을 폐기합니다. Authorization 헤더는 필요하지 않으며, 이미 발급된 액세스 토큰은 만료 시각까지 유효합니다.")
  @ApiResponse(responseCode = "204", description = "로그아웃 완료")
  public void logout(@Valid @RequestBody RefreshTokenRequest request) {
    service.logout(request.refreshToken());
  }
}
