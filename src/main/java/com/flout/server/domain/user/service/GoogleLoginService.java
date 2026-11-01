package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.*;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.GoogleTokenVerifier;
import com.flout.server.global.security.JwtService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleLoginService {
  private final UserRepository users;
  private final GoogleTokenVerifier verifier;
  private final JwtService tokens;
  private final PasswordEncoder passwords;

  public GoogleLoginService(
      UserRepository users,
      GoogleTokenVerifier verifier,
      JwtService tokens,
      PasswordEncoder passwords) {
    this.users = users;
    this.verifier = verifier;
    this.tokens = tokens;
    this.passwords = passwords;
  }

  @Transactional
  public LoginResponse login(GoogleLoginRequest request, UserRole requiredRole) {
    Jwt identity = verifier.verify(request.idToken());
    String email = identity.getClaimAsString("email").strip().toLowerCase(Locale.ROOT);
    User user = users.findByGoogleSubject(identity.getSubject()).orElse(null);
    if (user == null) {
      user = users.findForGoogleByEmail(email).orElse(null);
      if (user != null) {
        if (user.getRole() != requiredRole)
          throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
        if (user.getGoogleSubject() != null
            && !user.getGoogleSubject().equals(identity.getSubject())) {
          throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
        }
        if (user.getGoogleSubject() == null) {
          if (requiredRole == UserRole.RESIDENT) {
            throw new BaseException(UserErrorCode.EMAIL_LOGIN_REQUIRED);
          }
          // 기존 주민 계정 자동 연결 차단
          // 사전 승인된 Gmail·Workspace 운영자만 최초 연결
          String domain = identity.getClaimAsString("hd");
          boolean authoritative =
              email.endsWith("@gmail.com") || (domain != null && !domain.isBlank());
          if (requiredRole != UserRole.ADMIN || !authoritative) {
            throw new BaseException(UserErrorCode.GOOGLE_LINK_REQUIRED);
          }
          user.connectGoogle(identity.getSubject());
        }
      } else {
        if (requiredRole == UserRole.ADMIN)
          throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
        String name = identity.getClaimAsString("name");
        if (name == null || name.isBlank()) name = "주민";
        name = name.strip();
        if (name.length() > 50) name = name.substring(0, 50);
        user = User.resident(email, passwords.encode(UUID.randomUUID().toString()), name);
        user.connectGoogle(identity.getSubject());
        persistNew(user);
      }
    }
    if (user.getRole() != requiredRole) throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
    user.recordLogin();
    persist(user);
    return tokens.issue(user);
  }

  private void persistNew(User user) {
    try {
      persist(user);
    } catch (PessimisticLockingFailureException e) {
      // 동시 가입 중 갭 락 충돌 시 재로그인 안내
      throw new BaseException(UserErrorCode.GOOGLE_LINK_REQUIRED);
    }
  }

  private void persist(User user) {
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // 고유 제약으로 동시 가입·연결 차단 후 재로그인 안내
      throw new BaseException(UserErrorCode.GOOGLE_LINK_REQUIRED);
    }
  }
}
