package com.flout.server.domain.address.response.code;

import static com.flout.server.global.constant.StaticValue.*;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AddressSuccessCode implements BaseResponseCode {
  ADDRESS_SEARCH_SUCCESS(OK, "ADDRESS_200_1", "주소 검색에 성공했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
