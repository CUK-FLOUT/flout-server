package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.dto.request.RegisterFcmTokenRequest;
import com.flout.server.domain.notification.dto.response.RegisterFcmTokenResponse;
import com.flout.server.domain.notification.repository.FcmTokenRepository;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmTokenService {
  private final UserRepository users;
  private final FcmTokenRepository tokens;

  // 현재 사용자 토큰 등록 및 본인 이전 토큰 교체
  @Transactional
  public RegisterFcmTokenResponse register(Long userId, RegisterFcmTokenRequest request) {
    if (!users.existsById(userId)) throw new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR);
    String hash = hash(request.token());
    tokens.register(userId, request.token(), hash);
    if (request.previousToken() != null && !request.previousToken().equals(request.token())) {
      tokens.deleteByUserIdAndTokenHash(userId, hash(request.previousToken()));
    }
    return new RegisterFcmTokenResponse(tokens.findByTokenHash(hash).orElseThrow().getId());
  }

  // 긴 토큰 대신 고정 길이 해시로 중복 판별
  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
