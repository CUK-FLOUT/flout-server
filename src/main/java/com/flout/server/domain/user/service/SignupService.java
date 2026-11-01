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
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class SignupService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public SignupService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional
  public SignupResponse signup(
      @NotNull(message = "회원가입 정보를 입력해주세요.") @Valid SignupRequest request) {
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
