package com.flout.server.domain.address.response.code;

import static com.flout.server.global.constant.StaticValue.*;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AddressErrorCode implements BaseResponseCode {
  ADDRESS_NOT_FOUND(NOT_FOUND, "ADDRESS_404_1", "검색 결과가 없습니다."),
  KAKAO_API_ERROR(BAD_GATEWAY, "ADDRESS_502_1", "주소 좌표 조회 서비스 호출에 실패했습니다."),
  KAKAO_API_TIMEOUT(SERVICE_UNAVAILABLE, "ADDRESS_503_1", "주소 좌표 조회 서비스 응답 시간이 초과되었습니다."),
  JUSO_API_ERROR(BAD_GATEWAY, "ADDRESS_502_2", "주소 검색 서비스 호출에 실패했습니다."),
  JUSO_API_TIMEOUT(SERVICE_UNAVAILABLE, "ADDRESS_503_2", "주소 검색 서비스 응답 시간이 초과되었습니다."),
  INVALID_COORDINATE_ADDRESS(BAD_REQUEST, "ADDRESS_400_1", "부천시의 구체적인 주소를 입력해주세요."),
  COORDINATE_ADDRESS_MISMATCH(NOT_FOUND, "ADDRESS_404_2", "선택한 주소와 일치하는 좌표를 찾을 수 없습니다.");

  private final int status;
  private final String code;
  private final String message;
}
