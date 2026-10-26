package com.flout.server.domain.user.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseResponseCode {
  EMAIL_ALREADY_EXISTS(409, "USER_409_1", "이미 가입된 이메일입니다."),
  PASSWORD_MISMATCH(400, "USER_400_1", "비밀번호 확인이 일치하지 않습니다."),
  PASSWORD_TOO_LONG(400, "USER_400_2", "비밀번호가 너무 깁니다. 더 짧게 입력해주세요."),
  LOGIN_FAILED(401, "USER_401_1", "이메일 또는 비밀번호가 올바르지 않습니다.");
  private final int status;
  private final String code;
  private final String message;
}
