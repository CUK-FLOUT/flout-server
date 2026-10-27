package com.flout.server.global.response.code;

import static com.flout.server.global.constant.StaticValue.*;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GeneralErrorCode implements BaseResponseCode {
  BAD_REQUEST_ERROR(BAD_REQUEST, "COMMON_400_1", "잘못된 요청입니다."),
  INVALID_HTTP_MESSAGE_BODY(BAD_REQUEST, "COMMON_400_2", "요청 본문의 형식이 잘못되었습니다."),
  INVALID_HTTP_MESSAGE_PARAMETER(BAD_REQUEST, "COMMON_400_3", "요청 매개변수의 형식이 잘못되었습니다."),
  UNAUTHORIZED_ERROR(UNAUTHORIZED, "COMMON_401_1", "인증되지 않은 유저는 해당 리소스에 접근할 수 없습니다."),
  EXPIRED_ACCESS_TOKEN(UNAUTHORIZED, "COMMON_401_2", "접근 토큰이 만료되었습니다."),
  INVALID_ACCESS_TOKEN(UNAUTHORIZED, "COMMON_401_3", "유효하지 않은 접근 토큰입니다."),
  ACCESS_DENIED_REQUEST(FORBIDDEN, "COMMON_403_1", "해당 리소스에 대한 접근 권한이 없습니다."),
  NOT_FOUND_ENDPOINT(NOT_FOUND, "COMMON_404_1", "존재하지 않는 요청 경로입니다. 요청 주소를 확인해주세요."),
  UNSUPPORTED_HTTP_METHOD(METHOD_NOT_ALLOWED, "COMMON_405_1", "지원하지 않는 요청 방식입니다."),
  NOT_ACCEPTABLE_ERROR(406, "COMMON_406_1", "지원하지 않는 응답 형식입니다."),
  UNSUPPORTED_MEDIA_TYPE_ERROR(415, "COMMON_415_1", "지원하지 않는 요청 본문 형식입니다."),
  SERVER_ERROR(INTERNAL_SERVER_ERROR, "COMMON_500_1", "서버 내부에서 알 수 없는 에러가 발생했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
