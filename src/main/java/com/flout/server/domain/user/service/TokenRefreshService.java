package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.JwtService;
import com.flout.server.global.security.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {
  private final RefreshTokenStore refreshTokens;
  private final UserRepository users;
  private final JwtService jwt;

  @Transactional
  public LoginResponse refresh(String raw) {
    Long userId = refreshTokens.consume(raw);
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(UserErrorCode.INVALID_REFRESH_TOKEN));
    return jwt.issue(user);
  }
}
