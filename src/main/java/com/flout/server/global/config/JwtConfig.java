package com.flout.server.global.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@Slf4j
public class JwtConfig {
  @Bean
  SecretKey jwtKey(@Value("${app.jwt.secret:}") String secret, Environment environment) {
    byte[] bytes;
    if (secret.isBlank()) {
      if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
        throw new IllegalStateException("JWT_SECRET 환경변수를 설정해주세요.");
      }
      bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      log.warn("로컬 임시 JWT 키를 사용합니다. 서버 재시작 시 기존 토큰은 무효화됩니다.");
    } else {
      bytes = Base64.getDecoder().decode(secret);
      if (bytes.length < 32) {
        throw new IllegalArgumentException("JWT_SECRET은 32바이트 이상인 Base64 키여야 합니다.");
      }
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtKey, @Value("${app.jwt.issuer:flout}") String issuer) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> requiredClaims =
        jwt -> {
          if (jwt.getExpiresAt() == null
              || jwt.getSubject() == null
              || jwt.getClaimAsString("role") == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
          }
          return OAuth2TokenValidatorResult.success();
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO),
            new JwtIssuerValidator(issuer),
            requiredClaims));
    return decoder;
  }
}
