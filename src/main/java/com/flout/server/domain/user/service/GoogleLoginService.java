package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.request.GoogleLoginRequest;
import com.flout.server.domain.user.dto.response.LoginResponse;
import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.*;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.GoogleTokenVerifier;
import com.flout.server.global.security.JwtService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleLoginService {
  private final UserRepository users;
  private final UserConsentRepository consents;
  private final GoogleTokenVerifier verifier;
  private final JwtService tokens;
  private final PasswordEncoder passwords;
  private final String thirdPartyProvisionVersion;
  private final String statusNotificationVersion;

  public GoogleLoginService(
      UserRepository users,
      UserConsentRepository consents,
      GoogleTokenVerifier verifier,
      JwtService tokens,
      PasswordEncoder passwords,
      @Value("${app.consent.third-party-provision-version:1.0}") String thirdPartyProvisionVersion,
      @Value("${app.consent.status-notification-version:1.0}") String statusNotificationVersion) {
    this.users = users;
    this.consents = consents;
    this.verifier = verifier;
    this.tokens = tokens;
    this.passwords = passwords;
    this.thirdPartyProvisionVersion = thirdPartyProvisionVersion;
    this.statusNotificationVersion = statusNotificationVersion;
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
          // 이메일만으로 기존 주민 계정을 자동 연결하지 않는다.
          // 운영자는 사전 승인된 Gmail/Workspace 주소에 한해 최초 연결한다.
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
        if (!Boolean.TRUE.equals(request.thirdPartyProvisionAgreed())
            || !Boolean.TRUE.equals(request.statusNotificationAgreed())) {
          throw new BaseException(UserErrorCode.GOOGLE_CONSENT_REQUIRED);
        }
        String name = identity.getClaimAsString("name");
        if (name == null || name.isBlank()) name = "주민";
        name = name.strip();
        if (name.length() > 50) name = name.substring(0, 50);
        user = User.resident(email, passwords.encode(UUID.randomUUID().toString()), name);
        user.connectGoogle(identity.getSubject());
        persist(user);
        LocalDateTime now = LocalDateTime.now();
        consents.saveAll(
            List.of(
                UserConsent.agree(
                    user, ConsentType.THIRD_PARTY_PROVISION, thirdPartyProvisionVersion, now),
                UserConsent.agree(
                    user, ConsentType.STATUS_NOTIFICATION, statusNotificationVersion, now)));
      }
    }
    if (user.getRole() != requiredRole) throw new BaseException(UserErrorCode.GOOGLE_LOGIN_DENIED);
    user.recordLogin();
    persist(user);
    return tokens.issue(user);
  }

  private void persist(User user) {
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // 동시 가입/연결은 고유 제약으로 차단하고 다시 로그인을 요청한다.
      throw new BaseException(UserErrorCode.GOOGLE_LINK_REQUIRED);
    }
  }
}
