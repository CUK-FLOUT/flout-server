package com.flout.server.domain.riskassessment.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RiskAssessmentErrorCode implements BaseResponseCode {
  ANALYSIS_UNAVAILABLE(422, "RISK_ASSESSMENT_422_1", "입력한 주소는 취약도 분석 대상 지역이 아닙니다."),
  AI_SERVICE_ERROR(502, "RISK_ASSESSMENT_502_1", "AI 취약도 분석 서비스 호출에 실패했습니다."),
  AI_SERVICE_TIMEOUT(503, "RISK_ASSESSMENT_503_1", "AI 취약도 분석 서비스 응답 시간이 초과되었습니다."),
  INVALID_AI_RESPONSE(502, "RISK_ASSESSMENT_502_2", "AI 취약도 분석 결과의 형식이 올바르지 않습니다."),
  RESULT_SERIALIZATION_FAILED(500, "RISK_ASSESSMENT_500_1", "취약도 분석 결과 저장에 실패했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
