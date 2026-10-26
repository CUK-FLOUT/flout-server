package com.flout.server.domain.address.response.code;

import static com.flout.server.global.constant.StaticValue.*;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AddressErrorCode implements BaseResponseCode {
  ADDRESS_NOT_FOUND(NOT_FOUND, "ADDRESS_404_1", "검색 결과가 없습니다."),
  KAKAO_API_ERROR(BAD_GATEWAY, "ADDRESS_502_1", "주소 검색 서비스 호출에 실패했습니다."),
  KAKAO_API_TIMEOUT(SERVICE_UNAVAILABLE, "ADDRESS_503_1", "주소 검색 서비스 응답 시간이 초과되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
