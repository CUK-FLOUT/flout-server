package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.request.SignupRequest;
import com.flout.server.domain.user.dto.response.SignupResponse;
import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.*;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.global.exception.BaseException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class SignupService {
  private final UserRepository users;
  private final UserConsentRepository consents;
  private final PasswordEncoder encoder;
  private final String thirdPartyProvisionVersion;
  private final String statusNotificationVersion;

  public SignupService(
      UserRepository users,
      UserConsentRepository consents,
      PasswordEncoder encoder,
      @Value("${app.consent.third-party-provision-version:1.0}") String thirdPartyProvisionVersion,
      @Value("${app.consent.status-notification-version:1.0}") String statusNotificationVersion) {
    this.users = users;
    this.consents = consents;
    this.encoder = encoder;
    this.thirdPartyProvisionVersion = thirdPartyProvisionVersion;
    this.statusNotificationVersion = statusNotificationVersion;
  }

  @Transactional
  public SignupResponse signup(@NotNull @Valid SignupRequest request) {
    if (!request.password().equals(request.passwordConfirm())) {
      throw new BaseException(UserErrorCode.PASSWORD_MISMATCH);
    }
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BaseException(UserErrorCode.PASSWORD_TOO_LONG);
    }
    String email = request.email().strip().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw new BaseException(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
    User user = User.resident(email, encoder.encode(request.password()), request.name().strip());
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // 동시 가입 요청도 DB의 이메일 고유 제약으로 차단
      if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation
          && isEmailConstraint(violation.getConstraintName())) {
        throw new BaseException(UserErrorCode.EMAIL_ALREADY_EXISTS);
      }
      throw e;
    }
    LocalDateTime agreedAt = LocalDateTime.now();
    consents.saveAll(
        List.of(
            UserConsent.agree(
                user, ConsentType.THIRD_PARTY_PROVISION, thirdPartyProvisionVersion, agreedAt),
            UserConsent.agree(
                user, ConsentType.STATUS_NOTIFICATION, statusNotificationVersion, agreedAt)));
    return SignupResponse.from(user);
  }

  private boolean isEmailConstraint(String name) {
    if (name == null) {
      return false;
    }
    String constraint = name.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    return constraint.equals("uk_users_email") || constraint.endsWith(".uk_users_email");
  }
}
