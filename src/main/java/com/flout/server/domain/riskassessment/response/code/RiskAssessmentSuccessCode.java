package com.flout.server.domain.riskassessment.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RiskAssessmentSuccessCode implements BaseResponseCode {
  ASSESSMENT_CREATED(201, "RISK_ASSESSMENT_201_1", "취약도 분석이 완료되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
