package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.SignupRequest;
import com.flout.server.domain.user.dto.response.SignupResponse;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.SignupService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증/인가", description = "회원가입, 로그인, 토큰 재발급 및 운영자 계정 정보 조회")
public class SignupController {
  private final SignupService signupService;

  @PostMapping("/signup")
  @Operation(summary = "주민 회원가입", description = "필수 약관에 동의한 주민 계정을 생성합니다. 관리자 역할은 지정할 수 없습니다.")
  public ResponseEntity<SuccessResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(UserSuccessCode.SIGNUP_SUCCESS.getStatus())
        .body(SuccessResponse.of(signupService.signup(request), UserSuccessCode.SIGNUP_SUCCESS));
  }
}
