package com.flout.server.domain.user.controller;

import com.flout.server.domain.user.dto.request.ConsentRequest;
import com.flout.server.domain.user.service.ConsentService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/consents")
@Tag(name = "인증/인가", description = "회원가입, 로그인, 토큰 재발급 및 운영자 계정 정보 조회")
public class ConsentController {
  private final ConsentService consents;

  @PostMapping
  @Operation(
      summary = "정보 제공 동의 저장",
      description =
          "로그인한 주민의 필수 정보 제공 동의 내역을 저장합니다. 이메일 및 구글 로그인 사용자 모두 동일하게 사용합니다. JWT 인증이 필요합니다.")
  public SuccessResponse<ConsentResult> agree(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest request) {
    consents.agree(Long.valueOf(jwt.getSubject()));
    return SuccessResponse.ok(new ConsentResult(false));
  }

  public record ConsentResult(
      @Schema(description = "필수 정보 제공 동의 필요 여부", example = "false") boolean requiresConsent) {}
}
