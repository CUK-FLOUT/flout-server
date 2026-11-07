package com.flout.server.domain.preventionguide.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PreventionGuideSuccessCode implements BaseResponseCode {
  GUIDE_GENERATED(200, "GUIDE_200_1", "맞춤 예방 가이드를 조회했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
