package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.LoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.LoginService;
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
public class LoginController {
  private final LoginService loginService;

  @PostMapping("/login")
  @Operation(summary = "주민 로그인", description = "이메일과 비밀번호로 주민을 인증하고 액세스 토큰을 발급합니다.")
  public SuccessResponse<LoginResponse> resident(@Valid @RequestBody LoginRequest request) {
    return SuccessResponse.of(
        loginService.login(request, UserRole.RESIDENT), UserSuccessCode.LOGIN_SUCCESS);
  }

  @PostMapping("/admin/login")
  @Operation(
      summary = "운영자 로그인",
      description = "미리 등록된 ADMIN 계정만 로그인할 수 있습니다.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @io.swagger.v3.oas.annotations.media.Content(
                      mediaType = "application/json",
                      examples =
                          @io.swagger.v3.oas.annotations.media.ExampleObject(
                              value =
                                  """
                      {
                        "email": "admin@example.com",
                        "password": "admin123"
                      }
                      """))))
  public SuccessResponse<LoginResponse> admin(@Valid @RequestBody LoginRequest request) {
    return SuccessResponse.of(
        loginService.login(request, UserRole.ADMIN), UserSuccessCode.LOGIN_SUCCESS);
  }
}
