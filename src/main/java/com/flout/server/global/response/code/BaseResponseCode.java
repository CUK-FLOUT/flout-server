package com.flout.server.global.response.code;

public interface BaseResponseCode {

  // 프레임워크 의존 없이 HTTP 상태 코드 표현
  int getStatus();

  String getCode();

  String getMessage();
}
