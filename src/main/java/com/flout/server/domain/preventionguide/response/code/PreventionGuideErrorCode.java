package com.flout.server.domain.preventionguide.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PreventionGuideErrorCode implements BaseResponseCode {
  ASSESSMENT_NOT_FOUND(404, "GUIDE_404_1", "취약도 분석 결과를 찾을 수 없습니다."),
  ASSESSMENT_ACCESS_DENIED(403, "GUIDE_403_1", "본인의 취약도 분석 결과만 사용할 수 있습니다."),
  ASSESSMENT_NOT_COMPLETED(409, "GUIDE_409_1", "완료된 취약도 분석 결과만 가이드를 생성할 수 있습니다."),
  AI_NOT_CONFIGURED(503, "GUIDE_503_1", "맞춤 예방 가이드 생성 서비스 설정이 필요합니다."),
  AI_SERVICE_TIMEOUT(503, "GUIDE_503_2", "맞춤 예방 가이드 생성 서비스 응답 시간이 초과되었습니다."),
  AI_SERVICE_ERROR(502, "GUIDE_502_1", "맞춤 예방 가이드 생성 서비스 호출에 실패했습니다."),
  INVALID_AI_RESPONSE(502, "GUIDE_502_2", "맞춤 예방 가이드 생성 결과의 형식이 올바르지 않습니다."),
  GUIDE_SAVE_FAILED(500, "GUIDE_500_1", "맞춤 예방 가이드 저장에 실패했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
