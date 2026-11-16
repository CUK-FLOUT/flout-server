package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.GoogleTokenVerifier;
import com.flout.server.global.security.JwtService;
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
    User user = users.findByGoogleSubject(identity.getSubject()).orElse(null);
    if (user == null) {
      user = resolveAccountByEmail(identity, requiredRole);
    } else {
      requireRole(user, requiredRole);
    }
    user.recordLogin();
    persist(user);
    return tokens.issue(user);
  }

  private User resolveAccountByEmail(Jwt identity, UserRole requiredRole) {
    String email = UserCredentials.normalizeEmail(identity.getClaimAsString("email"));
    User user = users.findForGoogleByEmail(email).orElse(null);
    if (user == null) {
      if (requiredRole != UserRole.RESIDENT) {
        throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
      }
      return registerResident(identity, email);
    }

    requireRole(user, requiredRole);
    String existingSubject = user.getGoogleSubject();
    if (existingSubject != null) {
      if (!existingSubject.equals(identity.getSubject())) {
        throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
      }
      return user;
    }
    if (requiredRole == UserRole.RESIDENT) {
      throw new BaseException(UserErrorCode.EMAIL_LOGIN_REQUIRED);
    }

    // 사전 승인된 Gmail·Workspace 운영자만 최초 연결
    String domain = identity.getClaimAsString("hd");
    boolean authoritativeEmail =
        email.endsWith("@gmail.com") || (domain != null && !domain.isBlank());
    if (requiredRole != UserRole.ADMIN || !authoritativeEmail) {
      throw new BaseException(UserErrorCode.GOOGLE_LINK_REQUIRED);
    }
    user.connectGoogle(identity.getSubject());
    return user;
  }

  private static void requireRole(User user, UserRole requiredRole) {
    if (user.getRole() != requiredRole) {
      throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
    }
  }

  private User registerResident(Jwt identity, String email) {
    String name = identity.getClaimAsString("name");
    name = name == null || name.isBlank() ? "주민" : name.strip();
    if (name.length() > 50) {
      name = name.substring(0, 50);
    }
    User user = User.resident(email, passwords.encode(UUID.randomUUID().toString()), name);
    user.connectGoogle(identity.getSubject());
    persistNew(user);
    return user;
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
