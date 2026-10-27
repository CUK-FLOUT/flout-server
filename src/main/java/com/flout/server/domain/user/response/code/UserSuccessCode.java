package com.flout.server.domain.user.response.code;

import static com.flout.server.global.constant.StaticValue.CREATED;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseResponseCode {
  SIGNUP_SUCCESS(CREATED, "USER_201_1", "회원가입이 완료되었습니다."),
  LOGIN_SUCCESS(200, "USER_200_1", "로그인되었습니다."),
  TOKEN_REFRESH_SUCCESS(200, "USER_200_2", "인증 토큰이 재발급되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
