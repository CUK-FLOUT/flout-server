package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.GoogleLoginService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증/인가", description = "회원가입, 로그인, 토큰 재발급 및 운영자 계정 정보 조회")
public class GoogleLoginController {
  private final GoogleLoginService service;

  @PostMapping("/google")
  @Operation(summary = "주민 Google 로그인", description = "신규 주민은 개인정보 제3자 제공 및 진행상태 알림 수신 동의 후 가입합니다.")
  public SuccessResponse<LoginResponse> resident(@Valid @RequestBody GoogleLoginRequest request) {
    return SuccessResponse.of(
        service.login(request, UserRole.RESIDENT), UserSuccessCode.LOGIN_SUCCESS);
  }

  @PostMapping("/admin/google")
  @Operation(summary = "운영자 Google 로그인", description = "사전 등록된 ADMIN 계정만 허용합니다.")
  public SuccessResponse<LoginResponse> admin(@Valid @RequestBody GoogleLoginRequest request) {
    return SuccessResponse.of(
        service.login(request, UserRole.ADMIN), UserSuccessCode.LOGIN_SUCCESS);
  }
}
