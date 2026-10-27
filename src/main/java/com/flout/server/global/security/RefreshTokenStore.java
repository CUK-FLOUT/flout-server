package com.flout.server.global.security;

import com.flout.server.domain.user.entity.RefreshToken;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.RefreshTokenRepository;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenStore {
  private final RefreshTokenRepository tokens;
  private final long expiresIn;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenStore(
      RefreshTokenRepository tokens,
      @Value("${app.jwt.refresh-token-seconds:604800}") long expiresIn) {
    if (expiresIn < 60 || expiresIn > 7776000) {
      throw new IllegalArgumentException("리프레시 토큰 유효 시간은 60~7776000초여야 합니다.");
    }
    this.tokens = tokens;
    this.expiresIn = expiresIn;
  }

  @Transactional
  public IssuedToken issue(User user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = now.plusSeconds(expiresIn);
    tokens.save(
        RefreshToken.create(
            user,
            hash(raw),
            LocalDateTime.ofInstant(now, ZoneOffset.UTC),
            LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
    return new IssuedToken(raw, expiresIn, expiresAt);
  }

  @Transactional
  public Long consume(String raw) {
    if (!raw.matches("[A-Za-z0-9_-]{43}")) throw invalid();
    String hash = hash(raw);
    var token = tokens.findByTokenHash(hash).orElseThrow(RefreshTokenStore::invalid);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (!token.getExpiresAt().isAfter(now)) throw invalid();
    Long userId = token.getUser().getId();
    // 조건부 DELETE에 성공한 요청 하나만 새 토큰을 발급한다.
    if (tokens.consume(hash, now) != 1) throw invalid();
    return userId;
  }

  private static BaseException invalid() {
    return new BaseException(UserErrorCode.INVALID_REFRESH_TOKEN);
  }

  private static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  public record IssuedToken(String token, long expiresIn, Instant expiresAt) {
    @Override
    public String toString() {
      return "IssuedToken[REDACTED]";
    }
  }
}
