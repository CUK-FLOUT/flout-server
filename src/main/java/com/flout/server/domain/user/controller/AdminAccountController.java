package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.response.AdminAccountResponse;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.domain.user.service.AdminAccountService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "인증/인가", description = "회원가입, 로그인, 토큰 재발급 및 운영자 계정 정보 조회")
public class AdminAccountController {
  private final AdminAccountService service;

  /**
   * 인증된 운영자의 계정 정보를 조회합니다.
   *
   * @param jwt 인증 과정에서 검증된 토큰으로, subject는 사용자 ID입니다.
   * @return 운영자의 이름과 관할 지역을 담은 성공 응답
   */
  @GetMapping("/me")
  @Operation(
      summary = "운영자 계정 정보 조회",
      description = "로그인한 운영자의 이름과 관할 지역을 반환합니다. 관할 지역이 미지정이면 jurisdiction은 null입니다.")
  public SuccessResponse<AdminAccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.of(
        service.getAccount(Long.valueOf(jwt.getSubject())), UserSuccessCode.ADMIN_ACCOUNT_SUCCESS);
  }
}
