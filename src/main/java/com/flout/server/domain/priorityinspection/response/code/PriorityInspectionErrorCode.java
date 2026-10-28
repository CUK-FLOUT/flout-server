package com.flout.server.domain.priorityinspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriorityInspectionErrorCode implements BaseResponseCode {
  JURISDICTION_ACCESS_DENIED(
      403, "PRIORITY_INSPECTION_403_1", "본인 관할 지역의 신청만 우선점검 대상으로 지정할 수 있습니다.");

  private final int status;
  private final String code;
  private final String message;
}
