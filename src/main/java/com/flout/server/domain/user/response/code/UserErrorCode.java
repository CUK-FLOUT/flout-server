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
  LOGIN_FAILED(401, "USER_401_1", "이메일 또는 비밀번호가 올바르지 않습니다."),
  GOOGLE_INVALID_TOKEN(401, "USER_401_2", "Google 인증 정보가 올바르지 않습니다."),
  GOOGLE_LOGIN_DENIED(403, "USER_403_1", "Google 로그인이 허용되지 않은 계정입니다."),
  GOOGLE_LINK_REQUIRED(409, "USER_409_2", "기존 계정에 Google 계정 연결이 필요합니다."),
  GOOGLE_CONSENT_REQUIRED(400, "USER_400_3", "신규 가입 시 개인정보 제3자 제공과 진행상태 알림 수신 동의가 필요합니다."),
  GOOGLE_NOT_CONFIGURED(503, "USER_503_1", "Google 로그인 설정이 필요합니다.");
  private final int status;
  private final String code;
  private final String message;
}
