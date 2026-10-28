package com.flout.server.domain.priorityinspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriorityInspectionSuccessCode implements BaseResponseCode {
  DESIGNATED(200, "PRIORITY_INSPECTION_200_1", "우선점검 대상으로 지정되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
