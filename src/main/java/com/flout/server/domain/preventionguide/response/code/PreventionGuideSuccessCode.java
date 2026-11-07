package com.flout.server.domain.preventionguide.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PreventionGuideSuccessCode implements BaseResponseCode {
  GUIDE_GENERATED(200, "GUIDE_200_1", "맞춤 예방 가이드를 조회했습니다."),
  GUIDE_FOUND(200, "GUIDE_200_2", "예방 가이드를 조회했습니다."),
  ITEM_UPDATED(200, "GUIDE_200_3", "체크리스트 진행 상태를 저장했습니다."),
  RESOLUTION_UPDATED(200, "GUIDE_200_4", "예방 가이드 최종 선택을 저장했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
