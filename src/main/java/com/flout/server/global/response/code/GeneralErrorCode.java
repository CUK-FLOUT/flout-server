package com.flout.server.global.response.code;

import static com.flout.server.global.constant.StaticValue.*;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GeneralErrorCode implements BaseResponseCode {
  BAD_REQUEST_ERROR(BAD_REQUEST, "COMMON_400_1", "잘못된 요청입니다."),
  INVALID_HTTP_MESSAGE_BODY(BAD_REQUEST, "COMMON_400_2", "HTTP 요청 바디의 형식이 잘못되었습니다."),
  INVALID_HTTP_MESSAGE_PARAMETER(BAD_REQUEST, "COMMON_400_3", "HTTP 요청 파라미터의 형식이 잘못되었습니다."),
  UNAUTHORIZED_ERROR(UNAUTHORIZED, "COMMON_401_1", "인증되지 않은 유저는 해당 리소스에 접근할 수 없습니다."),
  EXPIRED_ACCESS_TOKEN(UNAUTHORIZED, "COMMON_401_2", "Access Token이 만료되었습니다."),
  INVALID_ACCESS_TOKEN(UNAUTHORIZED, "COMMON_401_3", "유효하지 않은 Access Token입니다."),
  ACCESS_DENIED_REQUEST(FORBIDDEN, "COMMON_403_1", "해당 리소스에 대한 접근 권한이 없습니다."),
  NOT_FOUND_ENDPOINT(NOT_FOUND, "COMMON_404_1", "존재하지 않는 엔드포인트입니다. 요청 href를 확인해주세요."),
  UNSUPPORTED_HTTP_METHOD(METHOD_NOT_ALLOWED, "COMMON_405_1", "지원하지 않는 HTTP 메소드입니다."),
  NOT_ACCEPTABLE_ERROR(406, "COMMON_406_1", "지원하지 않는 응답 형식입니다."),
  UNSUPPORTED_MEDIA_TYPE_ERROR(415, "COMMON_415_1", "지원하지 않는 요청 Content-Type입니다."),
  SERVER_ERROR(INTERNAL_SERVER_ERROR, "COMMON_500_1", "서버 내부에서 알 수 없는 에러가 발생했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
