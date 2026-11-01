package com.flout.server.domain.notification.controller;

import com.flout.server.domain.notification.dto.request.RegisterFcmTokenRequest;
import com.flout.server.domain.notification.dto.response.RegisterFcmTokenResponse;
import com.flout.server.domain.notification.response.code.NotificationSuccessCode;
import com.flout.server.domain.notification.service.FcmTokenService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/tokens")
@Tag(name = "알림", description = "로그인 사용자의 기기 FCM 토큰 관리")
public class FcmTokenController {
  private final FcmTokenService tokens;

  @PutMapping
  @Operation(
      summary = "FCM 토큰 등록·갱신",
      description =
          "JWT 사용자에게 기기 토큰을 연결합니다. 같은 토큰은 중복 저장하지 않으며 다른 계정에 연결돼 있으면 현재 사용자로 변경합니다. previousToken을 보내면 본인 이전 토큰만 교체하고, 생략하면 다른 기기 토큰은 유지합니다. Firebase 토큰 유효성 확인·푸시 발송은 수행하지 않습니다.")
  public ResponseEntity<SuccessResponse<RegisterFcmTokenResponse>> register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterFcmTokenRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(
            tokens.register(Long.valueOf(jwt.getSubject()), request),
            NotificationSuccessCode.FCM_TOKEN_REGISTERED));
  }
}
